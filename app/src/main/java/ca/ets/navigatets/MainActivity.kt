package ca.ets.navigatets

import android.os.Bundle
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import ca.ets.navigatets.databinding.ActivityMainBinding
import ca.ets.navigatets.objectsDetection.ChairOccupancyActivity
import ca.ets.navigatets.describe.DescribeModeChooserActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_navigate, R.id.navigation_notifications)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        
        // Custom bottom navigation handling
        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    navController.popBackStack(R.id.navigation_home, false)
                    true
                }
                R.id.navigation_navigate -> {
                    // Ouvre le choix du mode de recherche
                    if (navController.currentDestination?.id != R.id.chooseResearchModeFragment) {
                        navController.navigate(R.id.chooseResearchModeFragment)
                    }
                    true
                }
                R.id.navigation_chairs -> {
                    // Launch the chair detection activity from bottom navigation
                    startActivity(Intent(this, ChairOccupancyActivity::class.java))
                    true
                }
                R.id.navigation_describe -> {
                    // Launch the description mode chooser activity
                    startActivity(Intent(this, DescribeModeChooserActivity::class.java))
                    true
                }
                R.id.navigation_notifications -> {
                    if (navController.currentDestination?.id != R.id.navigation_notifications) {
                        navController.navigate(R.id.navigation_notifications)
                    }
                    true
                }
                else -> false
            }
        }
        
        // Keep bottom navigation in sync with back stack changes
        navController.addOnDestinationChangedListener { _, destination, _ ->
            navView.menu.findItem(destination.id)?.isChecked = true
        }
    }
}