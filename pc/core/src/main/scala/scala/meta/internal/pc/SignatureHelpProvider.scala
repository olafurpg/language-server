package scala.meta.internal.pc

import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureInformation
import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.meta.internal.semanticdb.scalac.SemanticdbOps
import scala.meta.pc
import scala.meta.pc.MethodInformation
import scala.meta.pc.SymbolIndexer
import scala.meta.pc.SymbolVisitor
import scala.tools.nsc.interactive.Global
import scala.util.control.NonFatal

class SignatureHelpProvider(val compiler: Global, indexer: SymbolIndexer) {
  import compiler._

  lazy val semanticdbOps: SemanticdbOps {
    val global: compiler.type
  } = new SemanticdbOps {
    val global: compiler.type = compiler
  }

  case class Callsite(
      qual: Tree,
      // TODO(olafur) use List instead of Array.
      tparams: Array[Tree],
      argss: Array[Array[Tree]]
  ) {
    def margss: Array[Array[Tree]] =
      if (tparams.isEmpty) argss
      else tparams +: argss
  }
  object curry {
    def unapply(tree: Tree): Option[Callsite] = tree match {
      case Apply(qual, args) =>
        var tparams: List[Tree] = Nil
        def loop(
            t: Tree,
            paramss: List[List[Symbol]],
            accum: List[List[Tree]]
        ): List[List[Tree]] =
          (t, paramss) match {
            case (Apply(qual0, args0), _ :: tail) =>
              loop(qual0, tail, args0 :: accum)
            case (TypeApply(_, args0), _) =>
              tparams = args0
              accum
            case _ =>
              accum
          }
        val argss = qual.symbol.paramss match {
          case _ :: tail =>
            loop(qual, tail, args :: Nil)
          case _ =>
            args :: Nil
        }
        Some(Callsite(qual, tparams.toArray, argss.map(_.toArray).toArray))
      case TypeApply(qual, targs) =>
        Some(Callsite(qual, targs.toArray, Array.empty))
      case _ => None
    }
  }

  def cursor(offset: Int, text: String): Option[Int] =
    if (offset > 0 && offset < text.length) {
      text.charAt(offset - 1) match {
        case '(' | '[' =>
          text.charAt(offset) match {
            case ')' | ']' =>
              Some(offset)
            case _ =>
              None
          }
        case _ =>
          None
      }
    } else {
      None
    }

  def methodInfo(symbol: Symbol): Option[MethodInformation] = {
    val sym = semanticdbSymbol(symbol)
    methodInfos.get(sym).orElse {
      if (!symbol.isJava) None
      else {
        index(sym)
        methodInfos.get(sym)
      }
    }
  }

  def semanticdbSymbol(symbol: Symbol): String = {
    import semanticdbOps._
    symbol.toSemantic
  }

  def index(symbol: String): Unit = {
    indexer.visit(
      symbol,
      new SymbolVisitor {
        override def visitMethod(method: MethodInformation): Unit = {
          methodInfos(method.symbol()) = method
        }
      }
    )
  }

  val methodInfos = TrieMap.empty[String, MethodInformation]

  def signatureHelp(
      filename: String,
      text: String,
      offset: Int
  ): SignatureHelp = {
    val unit = ScalaPC.addCompilationUnit(
      global = compiler,
      code = text,
      filename = filename,
      cursor = cursor(offset, text)
    )
    val pos = unit.position(offset)
    compiler.typeCheck(unit)
    var parent: Symbol = NoSymbol
    compiler.typedTreeAt(pos)
    var activeCallsite: Callsite = null
    var activeParameter: Int = -1
    var activeArg: Tree = EmptyTree
    var lastIneligible: Tree = EmptyTree
    val traverser = new compiler.Traverser {
      def isEligible(tree: Tree): Boolean = {
        val result = !tree.pos.isTransparent && tree.tpe != null
        if (!result) {
          lastIneligible = tree
        }
        result
      }
      def visit(tree: Tree): Unit = {
        tree match {
          case curry(site @ Callsite(qual, targs, argss)) =>
            for {
              (arg, i) <- Iterator(targs.iterator, argss.iterator.flatten).flatten.zipWithIndex
            } {
              if (arg.pos.includes(pos)) {
                activeCallsite = site
                activeParameter = i
                activeArg = arg
                parent = qual.symbol
                if (parent.isError) {
                  parent = compiler.typedTreeAt(qual.pos).symbol
                }
              }
              traverse(arg)
            }
            super.traverse(qual)
          case _ =>
            super.traverse(tree)
        }
      }
      // Tries to re-typecheck the tree standalone. Sometimes the compiler fails to typecheck
      // for example TypeApply nodes when the type paramter list is incomplete but the compiler
      // is able to typecheck the standalone TypeApply qualifiers.
      def retryTypecheck(tree: Tree): Unit = {
        val context = compiler.doLocateContext(tree.pos)
        val old = compiler.typer.context
        compiler.typer.context = context
        try {
          val typed = compiler.typer.typed(tree)
          if (typed.tpe != null) {
            tree.setType(typed.tpe)
            tree.symbol = typed.symbol
          }
        } catch {
          case NonFatal(_) => // do nothing
        } finally {
          compiler.typer.context = old
        }
      }
      override def traverse(tree: compiler.Tree): Unit = {
        if (isEligible(tree) && tree.pos.includes(pos)) {
          tree match {
            case TypeApply(qual, args) if tree.symbol == NoSymbol =>
              retryTypecheck(qual)
            case _ =>
          }
          visit(tree)
        }
      }
    }
    traverser.traverse(unit.body.asInstanceOf[compiler.Tree])
    val alternatives = parent match {
      case o: ModuleSymbol =>
        o.info.member(compiler.nme.apply).alternatives
      case o: ClassSymbol =>
        o.info.member(compiler.termNames.CONSTRUCTOR).alternatives
      case null =>
        Nil
      case m: MethodSymbol =>
        m.owner.asClass.info.member(parent.name).alternatives
      case _ =>
        parent.alternatives
    }
    val activeParent =
      if (parent.isOverloaded) parent.alternatives.head
      else parent
    if (alternatives.isEmpty) {
      null
    } else {
      var activeSignature = -1
      val infos = alternatives.iterator.zipWithIndex.collect {
        case (method: MethodSymbol, i) =>
          val isActiveSignature = method == activeParent
          val mparamss =
            if (method.typeParams.isEmpty) method.paramLists
            else method.typeParams :: method.paramLists
          val paramss = mparamss.map(_.toArray).toArray
          if (isActiveSignature) {
            activeSignature = i
            val paramCount = math.max(0, mparamss.foldLeft(-1)(_ + _.length))
            activeParameter = math.min(activeParameter, paramCount)
          }
          val argssA = activeCallsite.margss.iterator.flatten.toArray
          val argss = argssA.lift
          val info = methodInfo(method)
          val infoParamsA: Seq[pc.ParameterInformation] = info match {
            case Some(value) =>
              value.typeParameters().asScala ++
                value.parameters().asScala
            case None =>
              IndexedSeq.empty
          }
          val infoParams = infoParamsA.lift
          var k = 0
          val paramLabels: Seq[Seq[ParameterInformation]] =
            paramss.indices.map { i =>
              val params = paramss(i)
              params.indices.map { j =>
                val index = k
                k += 1
                val param = params(j)
                val paramInfo = infoParams(index)
                val name = paramInfo match {
                  case Some(value) => value.name()
                  case None => param.nameString
                }
                val label =
                  if (param.isTypeParameter) {
                    name
                  } else {
                    val default =
                      if (param.isParamWithDefault) " = {}"
                      else ""
                    if (isActiveSignature) {
                      val tpe = param.info.toLongString
                      s"$name: $tpe$default"
                    } else {
                      s"$name: ${param.info.toLongString}$default"
                    }
                  }
                val docstring =
                  paramInfo.map(_.docstring().orElse("")).getOrElse("")
                val lparam = new ParameterInformation(label, docstring)
                // TODO(olafur): use LSP 3.14.0 ParameterInformation.label offsets instead of strings
                // once this issue is fixed https://github.com/eclipse/lsp4j/issues/300
                if (isActiveSignature && index == activeParameter) {
                  argss(index) match {
                    case Some(a) if !a.tpe.isErroneous =>
                      val tpe = a.tpe.widen.toLongString
                      val typeString =
                        if (tpe.endsWith("=> Null")) {
                          tpe.stripSuffix("=> Null") + "=> ???"
                        } else {
                          tpe
                        }
                      if (!lparam.getLabel.endsWith(typeString)) {
                        val content = new MarkupContent()
                        content.setKind("markdown")
                        content.setValue(
                          "```scala\n" + typeString + "\n```\n" + docstring
                        )
                        lparam.setDocumentation(content)
                      }
                    case _ =>
                  }
                }
                lparam
              }
            }
          val methodSignature = paramLabels.iterator.zipWithIndex
            .map {
              case (params, i) =>
                if (method.typeParams.nonEmpty && i == 0) {
                  params.map(_.getLabel).mkString("[", ", ", "]")
                } else {
                  params.map(_.getLabel).mkString("(", ", ", ")")
                }
            }
            .mkString(
              method.nameString,
              "",
              s": ${method.returnType.toLongString}"
            )
          new SignatureInformation(
            methodSignature,
            info.fold("")(_.docstring()),
            paramLabels.iterator.flatten.toSeq.asJava
          )
      }.toIndexedSeq
      val help = new SignatureHelp()
      help.setSignatures(infos.asJava)
      if (activeSignature >= 0) {
        help.setActiveSignature(activeSignature)
      }
      if (activeParameter >= 0) {
        help.setActiveParameter(activeParameter)
      }
      help
    }
  }
}
