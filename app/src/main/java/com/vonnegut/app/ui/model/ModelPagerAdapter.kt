package com.vonnegut.app.ui.model

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class ModelPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount() = 2
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> InstalledModelsFragment()
        1 -> AvailableModelsFragment()
        else -> throw IllegalArgumentException("Invalid position $position")
    }
}
