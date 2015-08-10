package kamon.com.monsanto.arch.kamon.spray

import kamon.metric.CounterKey
import kamon.metric.instrument.UnitOfMeasurement

/** An ugly hack. */
package object can {
  def counterKey(name: String, unitOfMeasurement: UnitOfMeasurement = UnitOfMeasurement.Unknown): CounterKey =
    CounterKey(name, unitOfMeasurement)
}
