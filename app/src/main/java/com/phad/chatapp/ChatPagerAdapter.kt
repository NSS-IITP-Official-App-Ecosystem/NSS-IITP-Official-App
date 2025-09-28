package com.phad.chatapp

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Adapter for the chat tab interface using ViewPager2
 */
class ChatPagerAdapter(private val fragment: Fragment) :
    FragmentStateAdapter(fragment) {
    
    private val fragments = ArrayList<Fragment>()
    private val fragmentTitles = ArrayList<String>()
    
    /**
     * Add a fragment to the adapter
     */
    fun addFragment(fragment: Fragment, title: String) {
        fragments.add(fragment)
        fragmentTitles.add(title)
    }
    
    /**
     * Returns the number of fragments managed by the adapter
     */
    override fun getItemCount(): Int {
        return fragments.size
    }
    
    /**
     * Creates the fragment for the given position
     */
    override fun createFragment(position: Int): Fragment {
        return fragments[position]
    }
    
    /**
     * Returns the title for the tab at the specified position
     */
    fun getPageTitle(position: Int): CharSequence? {
        return fragmentTitles[position]
    }
}

/**
 * Simple placeholder fragment for tabs that aren't fully implemented
 */
class PlaceholderFragment(private val tabName: String) : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: android.os.Bundle?
    ): android.view.View? {
        // Create a simple TextView as placeholder
        val textView = android.widget.TextView(requireContext())
        textView.text = "Coming Soon: $tabName"
        textView.textSize = 20f
        textView.setTextColor(android.graphics.Color.WHITE)
        textView.gravity = android.view.Gravity.CENTER
        
        // Use a FrameLayout to center the text
        val frameLayout = android.widget.FrameLayout(requireContext())
        frameLayout.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        frameLayout.setBackgroundColor(android.graphics.Color.BLACK)
        
        // Add TextView to FrameLayout
        frameLayout.addView(textView)
        
        return frameLayout
    }
} 