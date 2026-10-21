/**
 * This file contains the view class for campsites.
 */

package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * View class for campsites.
 *
 * Should have all Composable functions related to campsites.
 */
class CampsiteView : ViewObserver<CampsiteModel> {
    override fun update(model: CampsiteModel) {
        // Not done.
    }
    @Composable
    fun SummaryView(campsiteList : List<Campsite>) : Unit {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(campsiteList) { index, campsite ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = campsite.name,
                            fontSize = 30.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}