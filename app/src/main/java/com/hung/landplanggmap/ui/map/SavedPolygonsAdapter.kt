package com.hung.landplanggmap.ui.map

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hung.landplanggmap.data.model.LandParcel
import com.hung.landplanggmap.databinding.ItemSavedPolygonsBinding
import com.hung.landplanggmap.utils.areaFormat
import com.hung.landplanggmap.ui.map.theme.getLandColorHex

class SavedPolygonsAdapter(
    private var items: ArrayList<LandParcel>,
    private val listener: LandItemClickListener
) : RecyclerView.Adapter<SavedPolygonsAdapter.MainHolder>() {

    fun renewItems(newItems: List<LandParcel>) {
        this.items.clear()
        this.items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: MainHolder, position: Int) {
        val land = items[position]
        holder.binding.lblTitle.text = land.ownerName
        holder.binding.lblArea.text = "Area: ${land.area.areaFormat()} m²"

        // Đổi màu nền theo loại đất
        val colorHex = getLandColorHex(land.landType)
        //holder.binding.root.setBackgroundColor(android.graphics.Color.parseColor(colorHex))

        holder.binding.btnDelete.setOnClickListener { listener.deleteLand(land) }
        holder.binding.btnCopy.setOnClickListener { listener.copyLand(land) }
        holder.binding.btnDisplay.setOnClickListener { listener.displayOnMap(land) }

        // Làm cho toàn bộ item clickable (tùy chọn)
        holder.itemView.setOnClickListener {
            listener.displayOnMap(land) // Gọi popup khi click vào item
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainHolder {
        val binding = ItemSavedPolygonsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MainHolder(binding)
    }

    class MainHolder(val binding: ItemSavedPolygonsBinding) :
        RecyclerView.ViewHolder(binding.root)
}