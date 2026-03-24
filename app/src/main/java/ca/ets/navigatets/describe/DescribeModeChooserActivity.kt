package ca.ets.navigatets.describe

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ca.ets.navigatets.R

class DescribeModeChooserActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_describe_mode_chooser)

        findViewById<android.view.View>(R.id.btn_photo).setOnClickListener {
            startActivity(Intent(this, DescribeSceneActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btn_video).setOnClickListener {
            startActivity(Intent(this, DescribeVideoActivity::class.java))
        }
    }
}
