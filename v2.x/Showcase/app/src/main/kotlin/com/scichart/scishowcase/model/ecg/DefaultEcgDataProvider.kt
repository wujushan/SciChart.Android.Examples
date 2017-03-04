package com.scichart.scishowcase.model.ecg

import android.content.Context
import android.util.Log
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.TimeUnit

class DefaultEcgDataProvider(context: Context) : IEcgDataProvider {
    //1. Heart rate or pulse rate (ECG HR)
    //2. Blood Pressure (NI BP)
    //3. Blood Volume (SV ml)
    //4. Blood Oxygenation (SPo2)
    private val ECG_TRACES = "data/EcgTraces.csv"
    private val TIME_INTERVAL = 1000L

    private var currentIndex: Int = 0
    private var totalIndex: Int = 0
    private var currentTrace = TraceAOrB.TraceA

    val xValues = ArrayList<Double>()
    val ecgHeartRate = ArrayList<Double>()
    val bloodPressure = ArrayList<Double>()
    val bloodVolume = ArrayList<Double>()
    val bloodOxygenation = ArrayList<Double>()

    val dataPublisher: PublishSubject<EcgData> = PublishSubject.create<EcgData>()
    var subscription: Disposable? = null

    init {
        try {
            val reader: BufferedReader = BufferedReader(InputStreamReader(context.assets.open(ECG_TRACES)))

            var line = reader.readLine()
            while (line != null) {
                val split = line.split(',')
                xValues.add(split[0].toDouble())
                ecgHeartRate.add(split[1].toDouble())
                bloodPressure.add(split[2].toDouble())
                bloodVolume.add(split[3].toDouble())
                bloodOxygenation.add(split[4].toDouble())

                line = reader.readLine()
            }
        } catch (ex: Exception) {
            Log.e("LOAD ECG", ex.message)
        }
    }

    override fun start() {
        subscription = Observable
                .interval(TIME_INTERVAL, TimeUnit.MICROSECONDS)
                .subscribeOn(Schedulers.computation())
                .doOnEach { sample() }
                .subscribe()
    }

    override fun stop() {
        dataPublisher.onComplete()
        subscription?.dispose()
        subscription = null
    }

    private fun sample() {
        appendPoint(800.0)
    }

    @Synchronized
    private fun appendPoint(sampleRate: Double) {
        if (currentIndex >= xValues.size) {
            currentIndex = 0
        }

        val time = totalIndex / sampleRate % 10
        val data = EcgData(time, ecgHeartRate[currentIndex], bloodPressure[currentIndex], bloodVolume[currentIndex], bloodOxygenation[currentIndex], currentTrace)

        dataPublisher.onNext(data)

        currentIndex++
        totalIndex++

        if (totalIndex % 8000 == 0) {
            currentTrace = if (currentTrace == TraceAOrB.TraceA) TraceAOrB.TraceB else TraceAOrB.TraceA
        }
    }

    override fun getEcgData(): Flowable<EcgData> = dataPublisher.toFlowable(BackpressureStrategy.BUFFER)
}