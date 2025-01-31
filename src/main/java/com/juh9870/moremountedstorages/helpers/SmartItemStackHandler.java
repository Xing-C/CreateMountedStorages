package com.juh9870.moremountedstorages.helpers;

import com.juh9870.moremountedstorages.ContraptionItemStackHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;

/**
 * ItemStackHandler but uses slot access methods instead of directly referencing this.storage, also includes check for slot validity
 */
public abstract class SmartItemStackHandler extends ContraptionItemStackHandler {

    public SmartItemStackHandler() {
    }

    public SmartItemStackHandler(int size) {
        super(size);
    }

    public SmartItemStackHandler(NonNullList<ItemStack> stacks) {
        super(stacks);
    }

    public SmartItemStackHandler(IItemHandler handler) {
        super(handler.getSlots());
        copyItemsOver(handler, this, handler.getSlots(), 0, 0);
    }

    protected boolean valid(int slot) {
        return true;
    }

    protected static void copyItemsOver(IItemHandler from, IItemHandler to, int size, int offsetFrom, int offsetTo) {
        boolean canClear = to instanceof IItemHandlerModifiable;
        for (int i = 0; i < size; i++) {
            if (canClear) {
                ((IItemHandlerModifiable) to).setStackInSlot(i + offsetTo, from.getStackInSlot(i + offsetFrom));
                continue;
            }
            to.extractItem(i + offsetTo, Integer.MAX_VALUE, false);
            if (!to.getStackInSlot(i + offsetTo).isEmpty()) {
                throw new RuntimeException("Can't free slot " + (i) + " in target item handler " + to.getClass());
            }

            // Item count is stored as byte in nbt,
            // which will result in negative item count in deserialized ItemStack instance
            // when item count is greater than 127.
            // So any item stack with size larger than 127
            // should be split and insert separately
            // to meet the assumption that count of ItemStack is always within Byte range.
            // TODO: Should the step be 64? Will 64 make it more vanilla compatible?
            ItemStack stack = from.getStackInSlot(i + offsetFrom);
            int step = Byte.MAX_VALUE;

            while (stack.getCount() > step) {
                to.insertItem(i + offsetTo, stack.split(step), false);
            }
            to.insertItem(i + offsetTo, stack, false);
        }
    }

    /**
     * @return {@link ItemStackHandler} that contains same items as this handler
     */
    protected ItemStackHandler simpleCopy() {
        ItemStackHandler target = new ItemStackHandler(getSlots());
        copyItemsOver(this, target, getSlots(), 0, 0);
        return target;
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (!valid(slot)) return stack;
        if (stack.isEmpty())
            return ItemStack.EMPTY;

        if (!isItemValid(slot, stack))
            return stack;

        validateSlotIndex(slot);

        ItemStack existing = getStackInSlot(slot);

        int limit = getStackLimit(slot, stack);

        if (!existing.isEmpty()) {
            if (!ItemHandlerHelper.canItemStacksStack(stack, existing))
                return stack;

            limit -= existing.getCount();
        }

        if (limit <= 0)
            return stack;

        boolean reachedLimit = stack.getCount() > limit;

        if (!simulate) {
            if (existing.isEmpty()) {
                setStackInSlot(slot, reachedLimit ? ItemHandlerHelper.copyStackWithSize(stack, limit) : stack);
            } else {
                existing.grow(reachedLimit ? limit : stack.getCount());
            }
            onContentsChanged(slot);
        }

        return reachedLimit ? ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - limit) : ItemStack.EMPTY;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!valid(slot)) return ItemStack.EMPTY;
        if (amount == 0) {
            return ItemStack.EMPTY;
        } else {
            this.validateSlotIndex(slot);
            ItemStack existing = getStackInSlot(slot);
            if (existing.isEmpty()) {
                return ItemStack.EMPTY;
            } else {
                int toExtract = Math.min(amount, existing.getMaxStackSize());
                if (existing.getCount() <= toExtract) {
                    if (!simulate) {
                        setStackInSlot(slot, ItemStack.EMPTY);
                        this.onContentsChanged(slot);
                        return existing;
                    } else {
                        return existing.copy();
                    }
                } else {
                    if (!simulate) {
                        setStackInSlot(slot, ItemHandlerHelper.copyStackWithSize(existing, existing.getCount() - toExtract));
                        this.onContentsChanged(slot);
                    }

                    return ItemHandlerHelper.copyStackWithSize(existing, toExtract);
                }
            }
        }
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return valid(slot) && super.isItemValid(slot, stack);
    }

    @Override
    protected void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= getSlots()) {
            throw new RuntimeException("Slot " + slot + " not in valid range - [0," + getSlots() + ")");
        }
    }
}
