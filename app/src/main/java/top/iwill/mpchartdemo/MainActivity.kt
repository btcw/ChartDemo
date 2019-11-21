package top.iwill.mpchartdemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.SeekBar
import androidx.collection.LruCache
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chart.setDataArray(xArrAy = arrayListOf("11.1", "11.2", "11.3", "11.4", "11.5", "11.6", "11.7", "11.8", "11.9", "11.10"),
            yArray = arrayListOf(10f, 24f, 115f, 26f, 110f, 66f, 47f, 88f, 90f, 0f),
            yArray2 = arrayListOf(10f, 2f, 30f, 160f, 10f, 66f, 47f, 83f, 90f, 0f))
    }
}
