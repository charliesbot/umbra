package com.charliesbot.umbra

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.charliesbot.terminal.GhosttyEngine
import com.charliesbot.terminal.TerminalConfig
import com.charliesbot.umbra.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val engine = GhosttyEngine()
        val ok = engine.initialize(TerminalConfig())
        binding.sampleText.text = if (ok) "Terminal engine initialized" else "Engine init failed"
        engine.destroy()
    }
}
