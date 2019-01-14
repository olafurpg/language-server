package scala.meta.internal.metals

case class WorkspaceSymbolQuery(
    query: String,
    combinations: Array[CharSequence]
)

object WorkspaceSymbolQuery {
  def apply(query: String): WorkspaceSymbolQuery = {
    WorkspaceSymbolQuery(query, Fuzzy.bloomFilterQueryStrings(query).toArray)
  }
  def fromQuery(query: String): Array[WorkspaceSymbolQuery] = {
    val isAllLowercase = query.forall(_.isLower)
    if (isAllLowercase) {
      // We special handle lowercase queries by guessing alternative capitalized queries.
      // Benchmark in akka/akka show that we pay a manageable performance overhead from this:
      // - "actorref" with guessed capitalization responds in 270ms.
      // - "ActorRef" with 0 guesses responds in 190ms.
      val buf = Array.newBuilder[WorkspaceSymbolQuery]
      // First, test the exact lowercase query.
      buf += WorkspaceSymbolQuery(query)
      // Second, uppercase all characters, this makes "fsmp" match "FiniteStateMachineProvider".
      buf += WorkspaceSymbolQuery(query.toUpperCase)
      // Third, uppercase only the first character, this makes "files" match "Files".
      buf += WorkspaceSymbolQuery(query.capitalize)
      // Fourth, uppercase the first character and up to two other characters, this makes "actorref" match "ActorRef"
      // and also "wosypro" match "WorkspaceSymbolProvider".
      buf ++= TrigramSubstrings.uppercased(query).map { u =>
        WorkspaceSymbolQuery(u, Fuzzy.bloomFilterQueryStrings(u).toArray)
      }
      buf.result()
    } else {
      Array(WorkspaceSymbolQuery(query))
    }
  }
}
