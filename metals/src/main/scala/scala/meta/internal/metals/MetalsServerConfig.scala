package scala.meta.internal.metals

object MetalsServerConfig {
  def isExtensionsEnabled: Boolean =
    System.getProperty("metals.extensions") != null
}
