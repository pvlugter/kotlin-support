package com.example.shoppingcart

import com.example.shoppingcart.Shoppingcart
import com.example.shoppingcart.persistence.Domain
import com.google.protobuf.Empty
import io.cloudstate.javasupport.eventsourced.CommandContext
import io.cloudstate.javasupport.eventsourced.EventSourcedEntityCreationContext
import io.cloudstate.kotlinsupport.annotations.EntityId
import io.cloudstate.kotlinsupport.annotations.eventsourced.*

import java.util.stream.Collectors
import com.example.shoppingcart.persistence.Domain

// tag::content[]
// tag::constructing[]
class ShoppingCartEntity(@EntityId private val entityId: String,
                         context: EventSourcedEntityCreationContext)
// end::constructing[]

// tag::entity-class[]
@EventSourcedEntity
class ShoppingCartEntity(@EntityId private val entityId: String) {
// end::entity-class[]

    // tag::entity-state[]
    private val cart: MutableMap<String, Shoppingcart.LineItem?> = mutableMapOf<String, Shoppingcart.LineItem?>()
    // end::entity-state[]

    // tag::snapshot[]
    @Snapshot
    fun snapshot(): Domain.Cart =
        Domain.Cart.newBuilder()
            .addAllItems(
                cart.values.stream()
                    .map { item: Shoppingcart.LineItem? -> this.convert(item) }
                    .collect(Collectors.toList())
            )
            .build()

    private fun convert(item: Shoppingcart.LineItem?): Domain.LineItem =
            Domain.LineItem.newBuilder()
                    .setProductId(item!!.productId)
                    .setName(item.name)
                    .setQuantity(item.quantity)
                    .build()
    // end::snapshot[]

    // tag::handle-snapshot[]
    @SnapshotHandler
    fun handleSnapshot(cart: Domain.Cart) {
        this.cart.clear()
        for (item in cart.itemsList) {
            this.cart[item.productId] = convert(item)
        }
    }
    // end::handle-snapshot[]

    // tag::item-added[]
    @EventHandler
    fun itemAdded(itemAdded: Domain.ItemAdded) {
        var item = cart[itemAdded.item.productId]

        item = if (item == null) {
            convert(itemAdded.item)
        } else {
            item.toBuilder()
                    .setQuantity(item.quantity + itemAdded.item.quantity)
                    .build()
        }
        cart[item!!.productId] = item
    }
    // end::item-added[]

    // tag::item-removed[]
    @EventHandler
    fun itemRemoved(itemRemoved: Domain.ItemRemoved): Shoppingcart.LineItem? = cart.remove(itemRemoved.productId)

    private fun convert(item: Domain.LineItem): Shoppingcart.LineItem =
        Shoppingcart.LineItem.newBuilder()
            .setProductId(item.productId)
            .setName(item.name)
            .setQuantity(item.quantity)
            .build()
    // end::item-removed[]

    // tag::get-cart[]
    @CommandHandler
    fun getCart(): Shoppingcart.Cart = Shoppingcart.Cart.newBuilder().addAllItems(cart.values).build()
    // end::get-cart[]

    // tag::add-item[]
    @CommandHandler
    fun addItem(item: Shoppingcart.AddLineItem, ctx: CommandContext): Empty {
        if (item.quantity <= 0) {
            ctx.fail("Cannot add negative quantity of to item ${item.productId}" )
        }
        ctx.emit(
            Domain.ItemAdded.newBuilder()
                .setItem(
                    Domain.LineItem.newBuilder()
                        .setProductId(item.productId)
                        .setName(item.name)
                        .setQuantity(item.quantity)
                        .build())
                .build())
        return Empty.getDefaultInstance()
    }
    // end::add-item[]

    @CommandHandler
    fun removeItem(item: Shoppingcart.RemoveLineItem, ctx: CommandContext): Empty {
        if (!cart.containsKey(item.productId)) {
            ctx.fail("Cannot remove item ${item.productId} because it is not in the cart.")
        }
        ctx.emit(
            Domain.ItemRemoved.newBuilder()
                .setProductId(item.productId)
                .build())
        return Empty.getDefaultInstance()
    }

    // tag::register[]
    fun main() {
        cloudstate {
            eventsourced {
                entityService = ShoppingCartEntity::class
                descriptor = Shoppingcart.getDescriptor().findServiceByName("ShoppingCart")
                additionalDescriptors = mutableListOf(Shoppingcart.getDescriptor(), Domain.getDescriptor() )
            }
        }.start()
                .toCompletableFuture()
                .get()
    }
    // end::register[]

    // tag::options[]
    fun main() {
        cloudstate {
            config {
                host = "0.0.0.0"
                port = 8080
                loglevel = "INFO"
            }

            eventsourced {
                entityService = ShoppingCartEntity::class
                descriptor = Shoppingcart.getDescriptor().findServiceByName("ShoppingCart")
                additionalDescriptors = mutableListOf(Shoppingcart.getDescriptor(), Domain.getDescriptor() )
                snapshotEvery = 1
                persistenceId = "shopping-cart"
            }
        }.start()
                .toCompletableFuture()
                .get()
    }
    // end::options[]

}
// end::content[]
