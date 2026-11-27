/*
 * Copyright (C) 2019 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.feature.compose.editing

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkAdapter
import dev.octoshrimpy.quik.common.base.QkBindingViewHolder
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.forwardTouches
import dev.octoshrimpy.quik.common.widget.contactSection
import dev.octoshrimpy.quik.extensions.associateByNotNull
import dev.octoshrimpy.quik.model.Contact
import dev.octoshrimpy.quik.model.ContactGroup
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Recipient
import dev.octoshrimpy.quik.repository.ConversationRepository
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import dev.octoshrimpy.quik.databinding.ContactListItemBinding
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

class ComposeItemAdapter @Inject constructor(
    private val colors: Colors,
    private val conversationRepo: ConversationRepository
) : QkAdapter<ComposeItem, QkBindingViewHolder<ContactListItemBinding>>() {

    val clicks: Subject<ComposeItem> = PublishSubject.create()
    val longClicks: Subject<ComposeItem> = PublishSubject.create()

    private val numbersViewPool = RecyclerView.RecycledViewPool()
    private val disposables = CompositeDisposable()

    var recipients: Map<String, Recipient> = mapOf()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<ContactListItemBinding> {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ContactListItemBinding.inflate(layoutInflater, parent, false)

        binding.numbers.setRecycledViewPool(numbersViewPool)
        binding.numbers.adapter = PhoneNumberAdapter()
        binding.numbers.forwardTouches(binding.row)

        return QkBindingViewHolder(binding).apply {
            binding.row.setOnClickListener {
                val item = getItem(adapterPosition)
                clicks.onNext(item)
            }
            binding.row.setOnLongClickListener {
                val item = getItem(adapterPosition)
                longClicks.onNext(item)
                true
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<ContactListItemBinding>, position: Int) {
        val binding = holder.binding
        val prevItem = if (position > 0) getItem(position - 1) else null
        when (val item = getItem(position)) {
            is ComposeItem.New -> bindNew(binding, item.value)
            is ComposeItem.Recent -> bindRecent(binding, item.value, prevItem)
            is ComposeItem.Starred -> bindStarred(binding, item.value, prevItem)
            is ComposeItem.Person -> bindPerson(binding, item.value, prevItem)
            is ComposeItem.Group -> bindGroup(binding, item.value, prevItem)
        }
    }

    /** Heimdall-style pill header: shown only on the first row of a section. */
    private fun bindHeader(binding: ContactListItemBinding, show: Boolean, text: String = "", icon: Int = 0) {
        binding.index.isVisible = show
        binding.index.text = text
        binding.index.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        binding.index.compoundDrawablesRelative[0]?.let { drawable ->
            val size = (14 * binding.index.resources.displayMetrics.density).toInt()
            drawable.setBounds(0, 0, size, size)
            drawable.setTint(binding.index.currentTextColor)
            binding.index.setCompoundDrawablesRelative(drawable, null, null, null)
        }
    }

    private fun bindNew(binding: ContactListItemBinding, contact: Contact) {
        bindHeader(binding, false)

        binding.avatar.recipients = listOf(createRecipient(contact))

        binding.title.text = contact.numbers.joinToString { it.address }

        binding.subtitle.isVisible = false

        binding.numbers.isVisible = false
    }

    private fun bindRecent(binding: ContactListItemBinding, conversation: Conversation, prev: ComposeItem?) {
        bindHeader(binding, prev !is ComposeItem.Recent, icon = R.drawable.ic_history_black_24dp)

        binding.avatar.recipients = conversation.recipients

        binding.title.text = conversation.getTitle()

        binding.subtitle.isVisible = conversation.recipients.size > 1 && conversation.name.isBlank()
        binding.subtitle.text = conversation.recipients.joinToString(", ") { recipient ->
            recipient.contact?.name ?: recipient.address
        }
        binding.subtitle.collapseEnabled = conversation.recipients.size > 1

        binding.numbers.isVisible = conversation.recipients.size == 1
        (binding.numbers.adapter as PhoneNumberAdapter).data = conversation.recipients
                .mapNotNull { recipient -> recipient.contact }
                .flatMap { contact -> contact.numbers }
    }

    private fun bindStarred(binding: ContactListItemBinding, contact: Contact, prev: ComposeItem?) {
        bindHeader(binding, prev !is ComposeItem.Starred, text = "★")

        binding.avatar.recipients = listOf(createRecipient(contact))

        binding.title.text = contact.name

        binding.subtitle.isVisible = false

        binding.numbers.isVisible = true
        (binding.numbers.adapter as PhoneNumberAdapter).data = contact.numbers
    }

    private fun bindGroup(binding: ContactListItemBinding, group: ContactGroup, prev: ComposeItem?) {
        bindHeader(binding, prev !is ComposeItem.Group, icon = R.drawable.ic_people_black_24dp)

        binding.avatar.recipients = group.contacts.map(::createRecipient)

        binding.title.text = group.title

        binding.subtitle.isVisible = true
        binding.subtitle.text = group.contacts.joinToString(", ") { it.name }
        binding.subtitle.collapseEnabled = group.contacts.size > 1

        binding.numbers.isVisible = false
    }

    private fun bindPerson(binding: ContactListItemBinding, contact: Contact, prev: ComposeItem?) {
        val section = contactSection(contact.name)
        bindHeader(binding, prev !is ComposeItem.Person || contactSection(prev.value.name) != section, text = section)

        binding.avatar.recipients = listOf(createRecipient(contact))

        binding.title.text = contact.name

        binding.subtitle.isVisible = false

        binding.numbers.isVisible = true
        (binding.numbers.adapter as PhoneNumberAdapter).data = contact.numbers
    }

    /** Alphabet-rail sections: label to first adapter position, in list order. */
    fun sections(): List<Pair<String, Int>> {
        val sections = linkedMapOf<String, Int>()
        data.forEachIndexed { index, item ->
            val key = when (item) {
                is ComposeItem.Starred -> "★"
                is ComposeItem.Person -> contactSection(item.value.name)
                else -> null
            }
            if (key != null && key !in sections) sections[key] = index
        }
        return sections.toList()
    }

    private fun createRecipient(contact: Contact): Recipient {
        return recipients[contact.lookupKey] ?: Recipient(
            address = contact.numbers.firstOrNull()?.address ?: "",
            contact = contact)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        disposables += conversationRepo.getUnmanagedRecipients()
                .map { recipients -> recipients.associateByNotNull { recipient -> recipient.contact?.lookupKey } }
                .subscribe { recipients -> this@ComposeItemAdapter.recipients = recipients }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        disposables.clear()
    }

    override fun areItemsTheSame(old: ComposeItem, new: ComposeItem): Boolean {
        val oldIds = old.getContacts().map { contact -> contact.lookupKey }
        val newIds = new.getContacts().map { contact -> contact.lookupKey }
        return oldIds == newIds
    }

    override fun areContentsTheSame(old: ComposeItem, new: ComposeItem): Boolean {
        return false
    }

}
