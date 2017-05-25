package com.olegych.scastie
package balancer

import api._

import akka.actor.{ActorLogging, Actor, ActorRef, Props, ActorSelection}
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import akka.stream.actor.ActorPublisherMessage.Request

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._

import scala.collection.mutable.{Queue => MQueue, Buffer}

case object SubscribeStatus
case class LoadBalancerUpdate(newBalancer: LoadBalancer[String, ActorSelection])

class StatusActor extends Actor with ActorLogging {
  val publishers = Buffer.empty[ActorRef]

  def receive = {
    case SubscribeStatus => {
      val publisher = context.actorOf(Props(new StatusForwarder()))
      publishers += publisher

      val source = 
        Source.fromPublisher(ActorPublisher[StatusProgress](publisher))
              .keepAlive(
                FiniteDuration(1, TimeUnit.SECONDS),
                () => StatusKeepAlive
              )

      sender ! source
    }
    case LoadBalancerUpdate(newBalancer) => {
      publishers.foreach(_ ! convert(newBalancer))
    }
  }

  private def convert(newBalancer: LoadBalancer[String, ActorSelection]): StatusProgress = {
    StatusInfo(
      newBalancer.servers.map(server =>
        Runner(server.mailbox.map(_.snippetId))
      )
    )
  }
}

class StatusForwarder() extends Actor with ActorPublisher[StatusProgress] {
  var buffer = MQueue.empty[StatusProgress]
  val maxSize = 10

  def receive = {
    case progress: StatusProgress => {
      if(buffer.size >= maxSize) {
        buffer.dequeue()
      }
      buffer.enqueue(progress)
      deliver()
    }
    case _: Request => {
      deliver()
    }
  }

  private def deliver(): Unit = {
    if (totalDemand > 0) {
      buffer.foreach { progress =>
        onNext(progress)
      }
      buffer.clear()
    }
  }
}