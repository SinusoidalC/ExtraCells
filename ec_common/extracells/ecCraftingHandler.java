package extracells;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import cpw.mods.fml.common.ICraftingHandler;

public class ecCraftingHandler implements ICraftingHandler
{

	@Override
	public void onCrafting(EntityPlayer player, ItemStack item, IInventory craftMatrix)
	{
		if (item.getItem() == extracells.Cell && item.getItemDamage() == 5)
		{
			if (!item.hasTagCompound())
			{
				item.setTagCompound(new NBTTagCompound());
			}
			item.getTagCompound().setInteger("costum_size", 4096);
			item.getTagCompound().setInteger("costum_types", 27);
		}
	}

	@Override
	public void onSmelting(EntityPlayer player, ItemStack item)
	{
		// TODO Auto-generated method stub

	}

}