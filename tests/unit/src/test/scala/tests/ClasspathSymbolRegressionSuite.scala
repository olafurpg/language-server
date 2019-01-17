package tests

import java.nio.file.Files
import scala.meta.internal.metals.RecursivelyDelete
import scala.meta.io.AbsolutePath

object ClasspathSymbolRegressionSuite extends BaseWorkspaceSymbolSuite {
  var tmp = AbsolutePath(Files.createTempDirectory("metals"))
  override def libraries: List[Library] = Libraries.suite
  def workspace: AbsolutePath = tmp
  override def afterAll(): Unit = {
    RecursivelyDelete(tmp)
  }

  check(
    "Map.Entry",
    """|com.google.common.collect.ImmutableMapEntry#NonTerminalImmutableBiMapEntry Class
       |com.google.common.collect.ImmutableMapEntry#NonTerminalImmutableMapEntry Class
       |com.google.common.collect.MapMakerInternalMap#AbstractStrongKeyEntry Class
       |com.google.common.collect.MapMakerInternalMap#AbstractWeakKeyEntry Class
       |com.google.common.collect.MapMakerInternalMap#DummyInternalEntry Class
       |com.google.common.collect.MapMakerInternalMap#EntryIterator Class
       |com.google.common.collect.MapMakerInternalMap#EntrySet Class
       |com.google.common.collect.MapMakerInternalMap#InternalEntry Interface
       |com.google.common.collect.MapMakerInternalMap#InternalEntryHelper Interface
       |com.google.common.collect.MapMakerInternalMap#StrongKeyDummyValueEntry Class
       |com.google.common.collect.MapMakerInternalMap#StrongKeyStrongValueEntry Class
       |com.google.common.collect.MapMakerInternalMap#StrongKeyWeakValueEntry Class
       |com.google.common.collect.MapMakerInternalMap#StrongValueEntry Interface
       |com.google.common.collect.MapMakerInternalMap#WeakKeyDummyValueEntry Class
       |com.google.common.collect.MapMakerInternalMap#WeakKeyStrongValueEntry Class
       |com.google.common.collect.MapMakerInternalMap#WeakKeyWeakValueEntry Class
       |com.google.common.collect.MapMakerInternalMap#WeakValueEntry Interface
       |com.google.common.collect.MapMakerInternalMap#WriteThroughEntry Class
       |com.google.common.reflect.MutableTypeToInstanceMap#UnmodifiableEntry Class
       |io.netty.util.collection.ShortCollections#UnmodifiableMap#EntryImpl Class
       |io.netty.util.internal.chmv8.ConcurrentHashMapV8#EntryIterator Class
       |io.netty.util.internal.chmv8.ConcurrentHashMapV8#EntrySetView Class
       |io.netty.util.internal.chmv8.ConcurrentHashMapV8#EntrySpliterator Class
       |io.netty.util.internal.chmv8.ConcurrentHashMapV8#ForEachEntryTask Class
       |io.netty.util.internal.chmv8.ConcurrentHashMapV8#ForEachTransformedEntryTask Class
       |io.netty.util.internal.chmv8.ConcurrentHashMapV8#MapEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#AbstractReferenceEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#EntryFactory Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#EntryIterator Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#EntrySet Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#NullEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#ReferenceEntry Interface
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#SoftEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#SoftEvictableEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#SoftExpirableEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#SoftExpirableEvictableEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#StrongEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#StrongEvictableEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#StrongExpirableEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#StrongExpirableEvictableEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#WeakEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#WeakEvictableEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#WeakExpirableEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#WeakExpirableEvictableEntry Class
       |jersey.repackaged.com.google.common.collect.MapMakerInternalMap#WriteThroughEntry Class
       |org.apache.commons.collections.map.AbstractInputCheckedMapDecorator#EntrySet Class
       |org.apache.commons.collections.map.AbstractInputCheckedMapDecorator#EntrySetIterator Class
       |org.apache.commons.collections.map.AbstractInputCheckedMapDecorator#MapEntry Class
       |org.apache.commons.collections.map.AbstractReferenceMap#ReferenceEntry Class
       |org.apache.commons.collections.map.AbstractReferenceMap#ReferenceEntrySet Class
       |org.apache.commons.collections.map.AbstractReferenceMap#ReferenceEntrySetIterator Class
       |org.jboss.netty.util.internal.ConcurrentIdentityWeakKeyHashMap#EntryIterator Class
       |org.jboss.netty.util.internal.ConcurrentIdentityWeakKeyHashMap#EntrySet Class
       |org.jboss.netty.util.internal.ConcurrentIdentityWeakKeyHashMap#HashEntry Class
       |org.jboss.netty.util.internal.ConcurrentIdentityWeakKeyHashMap#SimpleEntry Class
       |org.jboss.netty.util.internal.ConcurrentIdentityWeakKeyHashMap#WriteThroughEntry Class
       |""".stripMargin
  )

  check(
    "FileStream",
    """|javassist.CtClass#DelayedFileOutputStream Class
       |org.apache.avro.file.DataFileWriter#BufferedFileOutputStream Class
       |org.apache.commons.compress.compressors.pack200.TempFileCachingStreamBridge Class
       |org.apache.hadoop.fs.RawLocalFileSystem#LocalFSFileInputStream Class
       |org.apache.hadoop.fs.RawLocalFileSystem#LocalFSFileOutputStream Class
       |org.apache.hadoop.fs.shell.Display#AvroFileInputStream Class
       |org.apache.hadoop.hdfs.server.namenode.EditLogFileOutputStream Class
       |org.apache.hadoop.io.file.tfile.BoundedRangeFileInputStream Class
       |org.apache.spark.io.NioBufferedFileInputStream Class
       |org.apache.spark.sql.execution.streaming.CompactibleFileStreamLog Class
       |org.apache.spark.sql.execution.streaming.CompactibleFileStreamLog Object
       |org.jets3t.service.io.SegmentedRepeatableFileInputStream Class
       |""".stripMargin
  )
  check(
    "File",
    """|com.google.protobuf.compiler.PluginProtos#CodeGeneratorResponse#File Class
       |com.google.protobuf.compiler.PluginProtos#CodeGeneratorResponse#FileOrBuilder Interface
       |io.buoyant.config.types.File Class
       |io.buoyant.config.types.FileDeserializer Class
       |io.buoyant.config.types.FileSerializer Class
       |org.apache.commons.configuration.AbstractHierarchicalFileConfiguration Class
       |org.apache.commons.configuration.AbstractHierarchicalFileConfiguration#FileConfigurationDelegate Class
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#GetFileInfoRequestProto Class
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#GetFileInfoRequestProtoOrBuilder Interface
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#GetFileInfoResponseProto Class
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#GetFileInfoResponseProtoOrBuilder Interface
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#GetFileLinkInfoRequestProto Class
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#GetFileLinkInfoRequestProtoOrBuilder Interface
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#GetFileLinkInfoResponseProto Class
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#GetFileLinkInfoResponseProtoOrBuilder Interface
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#IsFileClosedRequestProto Class
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#IsFileClosedRequestProtoOrBuilder Interface
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#IsFileClosedResponseProto Class
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#IsFileClosedResponseProtoOrBuilder Interface
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#ListCorruptFileBlocksRequestProto Class
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#ListCorruptFileBlocksRequestProtoOrBuilder Interface
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#ListCorruptFileBlocksResponseProto Class
       |org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos#ListCorruptFileBlocksResponseProtoOrBuilder Interface
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#FileSummary Class
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#FileSummaryOrBuilder Interface
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#FilesUnderConstructionSection Class
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#FilesUnderConstructionSection#FileUnderConstructionEntry Class
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#FilesUnderConstructionSection#FileUnderConstructionEntryOrBuilder Interface
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#FilesUnderConstructionSectionOrBuilder Interface
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#INodeSection#FileUnderConstructionFeature Class
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#INodeSection#FileUnderConstructionFeatureOrBuilder Interface
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#INodeSection#INodeFile Class
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#INodeSection#INodeFileOrBuilder Interface
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#SnapshotDiffSection#FileDiff Class
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#SnapshotDiffSection#FileDiffOrBuilder Interface
       |org.apache.hadoop.mapred.SequenceFileAsBinaryInputFormat Class
       |org.apache.hadoop.mapred.SequenceFileAsBinaryInputFormat#SequenceFileAsBinaryRecordReader Class
       |org.apache.hadoop.mapred.lib.CombineSequenceFileInputFormat Class
       |org.apache.hadoop.mapred.lib.CombineSequenceFileInputFormat#SequenceFileRecordReaderWrapper Class
       |org.apache.hadoop.mapreduce.lib.input.CombineSequenceFileInputFormat Class
       |org.apache.hadoop.mapreduce.lib.input.CombineSequenceFileInputFormat#SequenceFileRecordReaderWrapper Class
       |org.apache.hadoop.mapreduce.lib.input.SequenceFileAsBinaryInputFormat Class
       |org.apache.hadoop.mapreduce.lib.input.SequenceFileAsBinaryInputFormat#SequenceFileAsBinaryRecordReader Class
       |org.apache.hadoop.yarn.server.nodemanager.WindowsSecureContainerExecutor#ElevatedFileSystem Class
       |org.apache.hadoop.yarn.server.nodemanager.WindowsSecureContainerExecutor#ElevatedFileSystem#ElevatedRawLocalFilesystem Class
       |org.jets3t.apps.cockpit.gui.LoginLocalFolderPanel#ProviderCredentialsFileTableModel Class
       |scala.reflect.io.File Class
       |scala.reflect.io.File Object
       |sourcecode.File Class
       |sourcecode.File Object
       |""".stripMargin
  )
  check(
    "Files",
    """|com.google.common.io.Files Class
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#FilesUnderConstructionSection Class
       |org.apache.hadoop.hdfs.server.namenode.FsImageProto#FilesUnderConstructionSectionOrBuilder Interface
       |org.apache.hadoop.mapred.MROutputFiles Class
       |org.apache.hadoop.mapred.Utils#OutputFileUtils#OutputFilesFilter Class
       |org.apache.hadoop.mapred.YarnOutputFiles Class
       |org.apache.hadoop.mapreduce.JobSubmissionFiles Class
       |org.apache.hadoop.yarn.server.nodemanager.WindowsSecureContainerExecutor#ElevatedFileSystem#ElevatedRawLocalFilesystem Class
       |org.apache.parquet.Files Class
       |org.apache.spark.sql.execution.command.ListFilesCommand Class
       |org.apache.spark.sql.execution.streaming.FileStreamSource.SeenFilesMap Class
       |org.langmeta.internal.io.ListFiles Class
       |scala.tools.nsc.interactive.CompilerControl#FilesDeletedItem Class
       |""".stripMargin
  )

  check(
    "Implicits",
    """|akka.actor.SupervisorStrategyLowPriorityImplicits Interface
       |org.apache.spark.sql.LowPrioritySQLImplicits Interface
       |org.apache.spark.sql.SQLImplicits Class
       |org.json4s.Implicits Interface
       |scala.LowPriorityImplicits Class
       |scala.io.LowPriorityCodecImplicits Interface
       |scala.math.Fractional.ExtraImplicits Interface
       |scala.math.Fractional.Implicits Object
       |scala.math.Integral.ExtraImplicits Interface
       |scala.math.Integral.Implicits Object
       |scala.math.LowPriorityOrderingImplicits Interface
       |scala.math.Numeric.ExtraImplicits Interface
       |scala.math.Numeric.Implicits Object
       |scala.math.Ordering.ExtraImplicits Interface
       |scala.math.Ordering.Implicits Object
       |scala.tools.nsc.interpreter.StdReplVals#ReplImplicits Class
       |scala.tools.nsc.typechecker.ContextErrors#TyperContextErrors#TyperErrorGen.MacroExpansionException.InferencerContextErrors#InferErrorGen.PolyAlternativeErrorKind.NamerContextErrors#NamerErrorGen.DuplicatesErrorKinds.ImplicitsContextErrors Interface
       |scala.tools.nsc.typechecker.Implicits Interface
       |scala.tools.nsc.typechecker.ImplicitsStats Object
       |""".stripMargin
  )

}
