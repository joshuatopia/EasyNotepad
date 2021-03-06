package com.joshuacerdenia.android.easynotepad.view.fragment

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.CheckBox
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.*
import com.google.android.material.snackbar.Snackbar
import com.joshuacerdenia.android.easynotepad.R
import com.joshuacerdenia.android.easynotepad.data.model.Note
import com.joshuacerdenia.android.easynotepad.databinding.FragmentNoteListBinding
import com.joshuacerdenia.android.easynotepad.extension.setVisibility
import com.joshuacerdenia.android.easynotepad.view.OnBackPressed
import com.joshuacerdenia.android.easynotepad.view.OnToolbarInflated
import com.joshuacerdenia.android.easynotepad.view.adapter.NoteAdapter
import com.joshuacerdenia.android.easynotepad.view.dialog.*
import com.joshuacerdenia.android.easynotepad.viewmodel.NoteListViewModel
import java.text.DateFormat.*
import java.util.*

class NoteListFragment : Fragment(), OnBackPressed, NoteAdapter.EventListener {

    interface Callbacks : OnToolbarInflated {

        fun onNoteSelected(noteID: UUID, query: String? = null)
    }

    private var _binding: FragmentNoteListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NoteListViewModel by viewModels()
    private lateinit var adapter: NoteAdapter
    private var callbacks: Callbacks? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        adapter = NoteAdapter(context, this)
        callbacks = context as Callbacks?
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteListBinding.inflate(inflater, container, false)
        callbacks?.onToolbarInflated(binding.toolbar)
        setupRecyclerView()
        return binding.root
    }

    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@NoteListFragment.adapter
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        viewModel.isManagingLive.observe(viewLifecycleOwner, { isManaging ->
            updateUI(isManaging)
        })

        viewModel.notesLive.observe(viewLifecycleOwner, { notes ->
            adapter.submitList(notes)
            binding.emptyTextView.setVisibility(notes.isEmpty())
        })

        viewModel.selectedNoteIDsLive.observe(viewLifecycleOwner, { noteIDs ->
            adapter.selectedItems = noteIDs
            // Update checkboxes:
            when (noteIDs.size){
                0 -> {
                    binding.selectAllCheckbox.isChecked = false
                    adapter.toggleCheckBoxes(false)
                }
                in 1 until adapter.itemCount -> {
                    binding.selectAllCheckbox.isChecked = false
                }
                adapter.itemCount -> {
                    binding.selectAllCheckbox.isChecked = true
                    adapter.toggleCheckBoxes(true)
                }
            }
        })

        binding.toolbar.setOnClickListener {
            binding.recyclerView.smoothScrollToPosition(0)
        }

        binding.selectAllCheckbox.setOnClickListener { checkBox ->
            if ((checkBox as CheckBox).isChecked) {
                val noteIDs = adapter.currentList.map { it.id }
                viewModel.replaceSelectedItems(noteIDs)
            } else {
                viewModel.clearSelectedItems()
            }
        }

        binding.fab.setOnClickListener {
            val note = Note() // Create blank note.
            viewModel.addNote(note)
            callbacks?.onNoteSelected(note.id)
        }

        parentFragmentManager.setFragmentResultListener(
            SortNotesFragment.ORDER,
            viewLifecycleOwner,
            { key, result ->
                result.getInt(key).run { viewModel.sortNotes(this) }
            })

        parentFragmentManager.setFragmentResultListener(
            ConfirmDeleteFragment.CONFIRM_DELETE,
            viewLifecycleOwner,
            { key, result ->
                val isConfirmed = result.getBoolean(key)
                if (isConfirmed) viewModel.deleteSelectedItems()
            })
    }

    private fun updateUI(isManaging: Boolean) {
        adapter.shouldShowCheckBoxes = isManaging
        binding.selectAllCheckbox.setVisibility(isManaging)
        binding.fab.setVisibility(!isManaging)
        binding.toolbar.title = if (!isManaging) getString(R.string.app_name) else null
        updateMenuItems(binding.toolbar.menu, isManaging)
        if (!isManaging) adapter.toggleCheckBoxes(false)
    }

    private fun updateMenuItems(menu: Menu, isManaging: Boolean) {
        menu.apply {
            findItem(R.id.menu_item_search)?.isVisible = !isManaging
            findItem(R.id.menu_item_manage)?.isVisible = !isManaging
            findItem(R.id.menu_item_sort)?.isVisible = !isManaging
            findItem(R.id.menu_item_delete)?.isVisible = isManaging
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_note_list, menu)
        updateMenuItems(menu, viewModel.isManaging())

        val searchItem = menu.findItem(R.id.menu_item_search)
        val searchView = searchItem.actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.submitQuery(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.submitQuery(newText ?: "")
                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_manage -> handleManageNotes()
            R.id.menu_item_sort -> handleSortNotes()
            R.id.menu_item_delete -> handleDeleteNotes()
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun handleManageNotes(): Boolean {
        viewModel.setIsManaging(true)
        return true
    }

    private fun handleSortNotes(): Boolean {
        SortNotesFragment.newInstance(viewModel.order)
            .show(parentFragmentManager, SortNotesFragment.TAG)
        return true
    }

    private fun handleDeleteNotes(): Boolean {
        if (viewModel.selectedItemCount > 0) {
            ConfirmDeleteFragment.newInstance(viewModel.selectedItemCount)
                .show(parentFragmentManager, ConfirmDeleteFragment.TAG)
        } else {
            Snackbar
                .make(binding.root, getString(R.string.nothing_selected), Snackbar.LENGTH_SHORT)
                .show()
        }

        return true
    }

    override fun onNoteClicked(noteID: UUID) {
        if (!viewModel.isManaging()) {
            callbacks?.onNoteSelected(noteID)
        }
    }

    override fun onNoteLongClicked(noteID: UUID) {
        // TODO: Select note
        viewModel.setIsManaging(!viewModel.isManaging())
        // viewModel.addSelection(noteID)
    }

    override fun onNoteCheckBoxClicked(noteID: UUID, isChecked: Boolean) {
        viewModel.apply {
            if (isChecked) addSelection(noteID) else removeSelection(noteID)
        }
    }

    override fun handleBackPress(): Boolean {
        return if (viewModel.isManaging()) {
            viewModel.setIsManaging(false)
            false
        } else {
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        callbacks = null
    }

    companion object {

        private const val TAG = "NoteListFragment"
        private const val HAS_TEXT_INTENT = "HAS_TEXT_INTENT"

        fun newInstance(hasTextIntent: Boolean = false): NoteListFragment {
            return NoteListFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(HAS_TEXT_INTENT, hasTextIntent)
                }
            }
        }
    }
}