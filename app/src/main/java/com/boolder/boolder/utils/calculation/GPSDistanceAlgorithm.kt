package com.boolder.boolder.utils.calculation

import com.boolder.boolder.domain.model.Problem
import kotlin.math.*
import android.util.Log

class GPSDistanceAlgorithm {

    fun pointsWithinDistance(dataPoints: List<Problem>, maxDist: Float): List<Pair<Problem, Problem>> {
        var closePoints: MutableList<Pair<Problem, Problem>> = mutableListOf();

        //brute force approach
/*        for (point1 in dataPoints) {
            for (point2 in dataPoints) {
                val dist = distanceFromPoints(point1, point2)
                if (point1.id != point2.id && dist < maxDist) {
                    closePoints.add(Pair(point1, point2))
                }
            }
        }*/

        //slightly better brute force approach
        for (i in 0 until dataPoints.size-1) {
            for (j in i+1 until dataPoints.size) {
                val dist = distanceFromPoints(dataPoints[i], dataPoints[j])
                if (dataPoints[i].id != dataPoints[j].id && dist < maxDist) {
                    closePoints.add(Pair(dataPoints[i], dataPoints[j]))
                }
            }
        }

        return closePoints
    }

    private fun distanceFromPoints(point1: Problem, point2: Problem): Double {
        //using Haversine formula and Geeks4Geeks algorithm
        var diffLat = toRadians(point2.latitude) - toRadians(point1.latitude)
        var diffLong = toRadians(point2.longitude) - toRadians(point1.longitude)

        var a = sin(diffLat / 2).pow(2) + cos(point1.latitude) * cos(point2.latitude) * sin(diffLong / 2).pow(2)
        var km = 2 * asin(sqrt(a)) * 6371

//        Log.i("distanceFromPoints", "p1: " + point1.name + " | p2: " + point2.name + " | dist: " + (km*1000).toString() + "m")
        return km*1000
    }

    private fun toRadians(degrees: Float): Double = degrees / 180.0 * PI

}
