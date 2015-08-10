package com.monsanto.arch.kamon.spray.can

import akka.actor.ExtensionKey

/** The extension key object for Akka IO.  To use, just do `IO(KamonHttp)`.
  *
  * @author Daniel Solano Gómez
  */
object KamonHttp extends ExtensionKey[KamonHttpExt]

