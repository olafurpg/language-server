package scala.meta.internal.metals

case class MetalsServerConfig(
    isExtensionsEnabled: Boolean =
      System.getProperty("metals.extensions") != "true",
    bloopProtocol: BloopProtocol = BloopProtocol.Auto,
    isHttpEnabled: Boolean =
      System.getProperty("metals.http") != "true"
)
object MetalsServerConfig {
  def default: MetalsServerConfig = MetalsServerConfig()
}
