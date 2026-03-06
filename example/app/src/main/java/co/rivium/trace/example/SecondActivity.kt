package co.rivium.trace.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import co.rivium.trace.sdk.RiviumTrace

class SecondActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(android.widget.TextView(this).apply {
            text = "Second Activity\n\nNavigation breadcrumb was automatically added.\n\nPress back to return."
            textSize = 18f
            setPadding(48, 48, 48, 48)
        })

        RiviumTrace.addUserBreadcrumb("SecondActivity opened")
    }

    override fun onDestroy() {
        RiviumTrace.addUserBreadcrumb("SecondActivity closed")
        super.onDestroy()
    }
}
