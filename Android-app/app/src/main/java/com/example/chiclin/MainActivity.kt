package com.example.chiclin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.chiclin.data.AppDataContainer
import com.example.chiclin.ui.ChiclinViewModel
import com.example.chiclin.ui.ChiclinViewModelFactory
import com.example.chiclin.ui.ComparacionScreen
import com.example.chiclin.ui.HistoricoScreen
import com.example.chiclin.ui.MesActualScreen
import com.example.chiclin.ui.theme.ChiclinTheme

class MainActivity : ComponentActivity() {

    private val appContainer by lazy { AppDataContainer(this) }

    private val viewModel: ChiclinViewModel by viewModels {
        ChiclinViewModelFactory(appContainer.entriesRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChiclinTheme {
                ChiclinApp(viewModel)
            }
        }
    }
}

private val tabTitles = listOf("Mes actual", "Comparación", "Histórico")

@Composable
fun ChiclinApp(viewModel: ChiclinViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                Text(
                    text = "Chiclin",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )
                TabRow(selectedTabIndex = selectedTab) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> MesActualScreen(viewModel)
                1 -> ComparacionScreen(viewModel)
                2 -> HistoricoScreen(viewModel)
            }
        }
    }
}
