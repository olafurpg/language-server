package scala.meta.internal.zipkin

object ZipkinUrls {
  def url: Option[String] = {
    pprint.log("HELLO WORDL!")
    val x = Option(System.getProperty("metals.zipkin.server.url"))
    pprint.log(x)
    x
  }
}
