package scala.meta.internal.metals

case class MetalsServerConfig(
    isExtensionsEnabled: Boolean =
      System.getProperty("metals.extensions") != null,
    bloopProtocol: BloopProtocol = BloopProtocol.Auto
)
object MetalsServerConfig {
  def default: MetalsServerConfig = MetalsServerConfig()
}
