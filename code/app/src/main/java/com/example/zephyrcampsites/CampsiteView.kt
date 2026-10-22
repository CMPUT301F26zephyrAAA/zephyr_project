package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * View class for campsites.
 *
 * Should have all Composable functions related to campsites.
 */
class CampsiteView : ViewObserver<CampsiteModel> {
    /**
     * Update function for MVC? But doesn't seem to be needed because of how Jetpack Compose works.
     */
    override fun update(model: CampsiteModel) {
        // ?
    }

    /**
     * Displaying the summary of a list of campsites.
     *
     * @param campsiteList List of campsites to display.
     * @param onClick (Temporary, for testing) Function to call when button is pressed.
     */
    @Composable
    fun SummaryView(
        campsiteList : List<Campsite>,
        onClick: () -> Unit
    ) {
        //var displayingList by remember {mutableStateOf(campsiteList)}
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.size(30.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        text = "Name",
                        fontSize = 15.sp,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        text = "Max Occupant",
                        fontSize = 15.sp,
                    )
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(campsiteList) { _, campsite ->
                    CampsiteRow(campsite)
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 30.dp)) {
                Button(onClick = onClick) {
                    Text("testing button")
                }
            }
        }
    }

    @Composable
    private fun CampsiteRow(item: Campsite) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = item.name,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = item.maxOccupancy.toString(),
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview
@Composable
fun TestPreview() {
    val camp1 = Campsite("1")
    camp1.name = "Test Site 1"
    camp1.maxOccupancy = 10
    val camp2 = Campsite("2")
    camp2.name = "Test Site 2"
    camp2.maxOccupancy = 20
    val campsites = listOf(camp1, camp2)
    val campsiteView = CampsiteView()
    campsiteView.SummaryView(campsiteList = campsites, onClick = {})
}