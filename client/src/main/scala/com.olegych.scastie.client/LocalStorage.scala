package com.olegych.scastie
package client

import scala.util.Try

import org.scalajs.dom.window.localStorage

import upickle.default.{write => uwrite, read => uread}

object LocalStorage {
  private val stateKey = "state"
  def save(state: ScastieState): Unit =
    localStorage.setItem(stateKey, uwrite(state))

  def load: Option[ScastieState] =
    Option(localStorage.getItem(stateKey))
      .flatMap(raw => Try(uread[ScastieState](raw)).toOption)
}
