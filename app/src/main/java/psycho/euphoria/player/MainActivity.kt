package psycho.euphoria.player

import android.app.Activity
import android.os.Bundle

class MainActivity : Activity() {

    private lateinit var mTimeBar: TimeBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mTimeBar = findViewById(R.id.time_bar)
        mTimeBar.duration = 100
        mTimeBar.position = 50

    }
}