package ca.ets.navigatets.ui.navigate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import ca.ets.navigatets.databinding.FragmentNavigateBinding
import ca.ets.navigatets.ui.home.HomeViewModel

class NavigateFragment : Fragment() {

    private var _binding: FragmentNavigateBinding? = null
    private val binding get() = _binding!!
    
    private val homeViewModel: HomeViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retain instance to preserve GL context
        retainInstance = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNavigateBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Observe selected location name
        homeViewModel.selectedLocationName.observe(viewLifecycleOwner) { name ->
            binding.textLocationName.text = name
        }

        // Observe selected floor plan image
        homeViewModel.selectedFloorPlanImageResId.observe(viewLifecycleOwner) { imageResId ->
            imageResId?.let {
                binding.imageFloorPlan.setImage(it)
            }
        }

        // Setup reset button
        binding.buttonReset.setOnClickListener {
            binding.imageFloorPlan.resetView()
        }

        return root
    }

    override fun onPause() {
        super.onPause()
        binding.imageFloorPlan.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.imageFloorPlan.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
