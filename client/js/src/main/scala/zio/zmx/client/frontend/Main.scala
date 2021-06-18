package zio.zmx.client.frontend

import com.raquo.laminar.api.L._
import io.laminext.websocket.PickleSocket.WebSocketReceiveBuilderBooPickleOps
import io.laminext.websocket.WebSocket
import boopickle.Default._
import com.raquo.airstream.split.Splittable
import org.scalajs.dom
import zio.Chunk
import zio.zmx.client.{ ClientMessage, MetricsMessage }
import zio.zmx.client.CustomPicklers.durationPickler
import zio.zmx.client.MetricsMessage.GaugeChange
import zio.zmx.internal.MetricKey
import animus._

object Main {

  implicit val splittable: Splittable[Chunk] = new Splittable[Chunk] {
    override def map[A, B](inputs: Chunk[A], project: A => B): Chunk[B] =
      inputs.map(project)
  }

  def main(args: Array[String]): Unit = {
    val _ = documentEvents.onDomContentLoaded.foreach { _ =>
      val appContainer = dom.document.querySelector("#app")
      appContainer.innerHTML = ""
      val _            = render(appContainer, view)
    }(unsafeWindowOwner)
  }

  val ws: WebSocket[MetricsMessage, ClientMessage] =
    WebSocket
      .url("ws://localhost:8089/ws")
      .pickle[MetricsMessage, ClientMessage]
      .build(reconnectRetries = Int.MaxValue)

  val messagesVar: Var[Map[MetricKey, MetricsMessage]] = Var(Map.empty)

  def GaugeView(key: MetricKey.Gauge, $gauge: Signal[GaugeChange]): Div = {
    println(s"GAUGE CREATION ${key}")
    val _        = $gauge
    var minGauge = Double.MaxValue
    var maxGauge = Double.MinValue

    val $offset: Signal[(Double, Double, Double)] =
      $gauge.map { gauge =>
        val value = gauge.value
        println(s"VALUE: ${value}")
        println(s"MIN: ${minGauge}")
        println(s"MAX: ${maxGauge}")
        minGauge = value min minGauge
        maxGauge = value max maxGauge

        val offset =
          if ((maxGauge - minGauge) == 0) 0
          else (value - minGauge) / (maxGauge - minGauge)

        (minGauge, offset, maxGauge)
      }

    div(
      div(
        strong(key.name)
      ),
//      $offset --> { offset =>
//        println(offset)
//      },
//      pre(
//        child.text <-- $offset.map(_._1),
//        child.text <-- $offset.map(_._2),
//        child.text <-- $offset.map(_._3)
//      ),
      pre(child.text <-- $offset.map(_._3.toString)),
      div(
        height("80px"),
        width("40px"),
        background("#333"),
        div(
          height("3px"),
          width("40px"),
          background("blue"),
          position.relative,
          top <-- $offset.map(_._2 * 77.0).spring.px
        )
      ),
      pre(child.text <-- $gauge.map(_.value.toString)),
      pre(child.text <-- $offset.map(_._1.toString))
    )
  }

  def messagesView: Div = div(
    children <-- messagesVar.signal.map(_.values.toList).split(_.key) { (key, _, $metric) =>
      key match {
        case key: MetricKey.Gauge =>
          GaugeView(key, $metric.asInstanceOf[Signal[GaugeChange]])
        case _                    => div("OOPS")
      }
    }
  )

  def view: Div =
    div(
      margin("0"),
      padding("20px"),
      position.relative,
      background("black"),
      height("100vh"),
      width("100vw"),
      color("white"),
      "METRICS",
      messagesView,
      ws.connect,
      ws.connected --> { _ =>
        ws.sendOne(ClientMessage.subscribe)
      },
      ws.received --> { (metricsMessage: MetricsMessage) =>
        metricsMessage match {
          case change: MetricsMessage.GaugeChange =>
            messagesVar.update(_.updated(change.key, change))
          case _                                  => ()
        }
      }
    )
}