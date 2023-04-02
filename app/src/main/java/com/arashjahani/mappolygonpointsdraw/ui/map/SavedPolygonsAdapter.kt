package com.arashjahani.mappolygonpointsdraw.ui.map

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arashjahani.mappolygonpointsdraw.data.entity.PolygonWithPoints
import com.arashjahani.mappolygonpointsdraw.databinding.ItemSavedPolygonsBinding
import com.arashjahani.mappolygonpointsdraw.utils.areaFormat

class SavedPolygonsAdapter(private var items: ArrayList<PolygonWithPoints>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private lateinit var listener: PolygonsItemClickListener

    fun setListener(listener: PolygonsItemClickListener) {
        this.listener = listener
    }

    override fun getItemCount(): Int {
        return items!!.size
    }

    fun renewItems(_items: List<PolygonWithPoints>){
        this.items.clear()
        this.items.addAll(_items)

        notifyDataSetChanged()
    }


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {


        var mainHolder = holder as MainHolder

        mainHolder.binding.lblTitle.text= items[position].polygon.title ?: ""
        mainHolder.binding.lblArea.text= "Area : ${items[position].polygon.area.areaFormat()} m²"

        mainHolder.binding.btnDelete.setOnClickListener {
            listener.deletePolygon(items[position].polygon._id)
        }

        mainHolder.binding.btnCopy.setOnClickListener {
            listener.copyPolygon(items[position])
        }

        mainHolder.binding.btnDisplay.setOnClickListener {
            listener.displayOnMap(items[position])
        }

    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val binding =
            ItemSavedPolygonsBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

        return MainHolder(
            binding
        )

    }

    class MainHolder(val binding: ItemSavedPolygonsBinding) :
        RecyclerView.ViewHolder(binding.root) {}

}