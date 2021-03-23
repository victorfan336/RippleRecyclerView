package com.victor.ripplerecyclerview

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.victor.ripplerecyclerview.databinding.ActivityMainBinding
import com.victor.ripplerecyclerview.view.RippleRecyclerView

class RippleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
        binding.recyclerView.layoutManager = RippleRecyclerView.ScrollerLinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerView.adapter = SimpleAdapter(this, ArrayList<String>().apply {
            for(i in 0..10) {
                add(i.toString())
            }
        })
        binding.recyclerView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.HORIZONTAL))
        binding.recyclerView.hasMoreData(true)
        binding.recyclerView.onCrossOverListener =  { isOverScroll ->
            Toast.makeText(this, "超过最大移动距离松开手：$isOverScroll", Toast.LENGTH_SHORT).show()
        }
    }

    class SimpleAdapter(private val context: Context, private val list: List<String>) : RecyclerView.Adapter<SimpleViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleViewHolder {
            return SimpleViewHolder(View.inflate(context, R.layout.item_text, null))
        }

        override fun onBindViewHolder(holder: SimpleViewHolder, position: Int) {
            holder.getTextView().text = list.getOrNull(position) ?: ""
        }

        override fun getItemCount() = list.size

    }

    class SimpleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun getTextView(): TextView = itemView.findViewById(R.id.tv_content)
    }

}