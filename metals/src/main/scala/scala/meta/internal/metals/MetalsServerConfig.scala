package scala.meta.internal.metals

case class MetalsServerConfig(
    isExtensionsEnabled: Boolean =
      System.getProperty("metals.extensions") != null,
    bloopProtocol: BloopProtocol = BloopProtocol.Auto,
    isHttpEnabled: Boolean =
      System.getProperty("metals.http") != null
)
object MetalsServerConfig {
  def default: MetalsServerConfig = MetalsServerConfig()
}
