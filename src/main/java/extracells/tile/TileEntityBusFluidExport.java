package extracells.tile;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet132TileEntityData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;
import net.minecraftforge.fluids.IFluidHandler;
import appeng.api.IAEItemStack;
import appeng.api.Util;
import appeng.api.WorldCoord;
import appeng.api.config.RedstoneModeInput;
import appeng.api.events.GridTileLoadEvent;
import appeng.api.events.GridTileUnloadEvent;
import appeng.api.me.tiles.IDirectionalMETile;
import appeng.api.me.tiles.IGridMachine;
import appeng.api.me.tiles.ITileCable;
import appeng.api.me.util.IGridInterface;
import appeng.api.me.util.IMEInventoryHandler;
import extracells.ItemEnum;
import extracells.SpecialFluidStack;
import extracells.gui.widget.WidgetFluidModes.FluidMode;
import static extracells.ItemEnum.*;

public class TileEntityBusFluidExport extends ColorableECTile implements IGridMachine, IDirectionalMETile, ITileCable
{
	private Boolean powerStatus = true, redstoneFlag = false, networkReady = true;
	private IGridInterface grid;
	private ItemStack[] filterSlots = new ItemStack[8];
	private String costumName = StatCollector.translateToLocal("tile.block.fluid.bus.export");
	private ArrayList<SpecialFluidStack> fluidsInNetwork = new ArrayList<SpecialFluidStack>();
	private ECPrivateInventory inventory = new ECPrivateInventory(filterSlots, costumName, 1);
	private RedstoneModeInput redstoneMode = RedstoneModeInput.Ignore;
	private FluidMode fluidMode = FluidMode.DROPS;

	@Override
	public void updateEntity()
	{
		if (!worldObj.isRemote && isPowered())
		{
			Boolean redstonePowered = worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord) || worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord + 1, zCoord);
			switch (getRedstoneMode())
			{
			case WhenOn:
				if (redstonePowered)
				{
					doWork(fluidMode);
				}
				break;
			case WhenOff:
				if (!redstonePowered)
				{
					doWork(fluidMode);
				}
				break;
			case OnPulse:
				if (!redstonePowered)
				{
					redstoneFlag = false;
				} else
				{
					if (!redstoneFlag)
					{
						doWork(fluidMode);
					} else
					{
						redstoneFlag = true;
						doWork(fluidMode);
					}
				}
				break;
			case Ignore:
				doWork(fluidMode);
				break;
			default:
				break;
			}
		}
	}

	@Override
	public void validate()
	{
		super.validate();
		MinecraftForge.EVENT_BUS.post(new GridTileLoadEvent(this, worldObj, getLocation()));
	}

	@Override
	public void invalidate()
	{
		super.invalidate();
		MinecraftForge.EVENT_BUS.post(new GridTileUnloadEvent(this, worldObj, getLocation()));
	}

	private void doWork(FluidMode mode)
	{
		ForgeDirection facing = ForgeDirection.getOrientation(getBlockMetadata());
		TileEntity facingTileEntity = worldObj.getBlockTileEntity(xCoord + facing.offsetX, yCoord + facing.offsetY, zCoord + facing.offsetZ);

		if (grid != null && facingTileEntity != null && facingTileEntity instanceof IFluidHandler)
		{
			IFluidHandler facingTank = (IFluidHandler) facingTileEntity;

			List<Fluid> fluidFilter = getFilterFluids(filterSlots);

			if (fluidFilter != null && fluidFilter.size() > 0)
			{
				for (Fluid entry : fluidFilter)
				{
					IMEInventoryHandler cellArray = getGrid().getCellArray();

					if (entry != null && cellArray != null)
					{
						IAEItemStack entryToAEIS = Util.createItemStack(new ItemStack(FLUIDDISPLAY.getItemEntry(), 1, entry.getID()));

						long contained = cellArray.countOfItemType(entryToAEIS);

						if (contained > 0)
						{
							exportFluid(new FluidStack(entry, contained < mode.getAmount() ? (int) contained : mode.getAmount()), facingTank, facing.getOpposite(), mode);
						}
					}
				}
			}
		}
	}

	public void exportFluid(FluidStack toExport, IFluidHandler tankToFill, ForgeDirection from, FluidMode mode)
	{
		if (toExport == null)
			return;

		int fillable = tankToFill.fill(from, toExport, false);

		if (fillable > 0)
		{
			int filled = tankToFill.fill(from, toExport, true);

			IAEItemStack toExtract = Util.createItemStack(new ItemStack(FLUIDDISPLAY.getItemEntry(), filled, toExport.fluidID));

			IMEInventoryHandler cellArray = grid.getCellArray();
			if (cellArray != null)
			{
				IAEItemStack extracted = cellArray.extractItems(toExtract);

				grid.useMEEnergy(mode.getCost(), "Export Fluid");

				if (extracted == null)
				{
					toExport.amount = filled;
					tankToFill.drain(from, toExport, true);
				} else if (extracted.getStackSize() < filled)
				{
					toExport.amount = (int) (filled - (filled - extracted.getStackSize()));
					tankToFill.drain(from, toExport, true);
				}
			}
		}
	}

	public List<Fluid> getFilterFluids(ItemStack[] filterItemStacks)
	{
		List<Fluid> filterFluids = new ArrayList<Fluid>();

		if (filterItemStacks != null)
		{
			for (ItemStack entry : filterItemStacks)
			{
				if (entry != null)
				{
					if (entry.getItem() instanceof IFluidContainerItem)
					{
						FluidStack contained = ((IFluidContainerItem) entry.getItem()).getFluid(entry);
						if (contained != null)
							filterFluids.add(contained.getFluid());
					}
					if (FluidContainerRegistry.isFilledContainer(entry))
					{
						filterFluids.add(FluidContainerRegistry.getFluidForFilledItem(entry).getFluid());
					}
				}
			}
		}
		return filterFluids;
	}

	public RedstoneModeInput getRedstoneMode()
	{
		return redstoneMode;
	}

	public void setRedstoneMode(RedstoneModeInput mode)
	{
		redstoneMode = mode;
	}

	public FluidMode getFluidMode()
	{
		return fluidMode;
	}

	public void setFluidMode(FluidMode mode)
	{
		fluidMode = mode;
	}

	@Override
	public Packet getDescriptionPacket()
	{
		NBTTagCompound nbtTag = getColorDataForPacket();
		this.writeToNBT(nbtTag);
		return new Packet132TileEntityData(this.xCoord, this.yCoord, this.zCoord, 1, nbtTag);
	}

	@Override
	public void onDataPacket(INetworkManager net, Packet132TileEntityData packet)
	{
		super.onDataPacket(net, packet);
		readFromNBT(packet.data);
	}

	@Override
	public WorldCoord getLocation()
	{
		return new WorldCoord(xCoord, yCoord, zCoord);
	}

	@Override
	public boolean isValid()
	{
		return true;
	}

	@Override
	public void setPowerStatus(boolean hasPower)
	{
		powerStatus = hasPower;
	}

	@Override
	public boolean isPowered()
	{
		return powerStatus;
	}

	@Override
	public IGridInterface getGrid()
	{
		return grid;
	}

	@Override
	public void setGrid(IGridInterface gi)
	{
		grid = gi;
	}

	@Override
	public World getWorld()
	{
		return worldObj;
	}

	@Override
	public boolean canConnect(ForgeDirection dir)
	{
		return dir.ordinal() != this.blockMetadata;
	}

	@Override
	public float getPowerDrainPerTick()
	{
		return 0;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		NBTTagList nbttaglist = new NBTTagList();

		for (int i = 0; i < this.filterSlots.length; ++i)
		{
			if (this.filterSlots[i] != null)
			{
				NBTTagCompound nbttagcompound1 = new NBTTagCompound();
				nbttagcompound1.setByte("Slot", (byte) i);
				this.filterSlots[i].writeToNBT(nbttagcompound1);
				nbttaglist.appendTag(nbttagcompound1);
			}
		}
		nbt.setTag("Items", nbttaglist);
		if (getInventory().isInvNameLocalized())
		{
			nbt.setString("CustomName", this.costumName);
		}

		nbt.setInteger("RedstoneMode", getRedstoneMode().ordinal());
		nbt.setInteger("FluidMode", getFluidMode().ordinal());
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		NBTTagList nbttaglist = nbt.getTagList("Items");
		this.filterSlots = new ItemStack[getInventory().getSizeInventory()];
		if (nbt.hasKey("CustomName"))
		{
			this.costumName = nbt.getString("CustomName");
		}
		for (int i = 0; i < nbttaglist.tagCount(); ++i)
		{
			NBTTagCompound nbttagcompound1 = (NBTTagCompound) nbttaglist.tagAt(i);
			int j = nbttagcompound1.getByte("Slot") & 255;

			if (j >= 0 && j < this.filterSlots.length)
			{
				this.filterSlots[j] = ItemStack.loadItemStackFromNBT(nbttagcompound1);
			}
		}
		inventory = new ECPrivateInventory(filterSlots, costumName, 1);

		setRedstoneMode(RedstoneModeInput.values()[nbt.getInteger("RedstoneMode")]);
		setFluidMode(FluidMode.values()[nbt.getInteger("FluidMode")]);
	}

	public ECPrivateInventory getInventory()
	{
		return inventory;
	}

	@Override
	public boolean coveredConnections()
	{
		return false;
	}

	public void setNetworkReady(boolean isReady)
	{
		networkReady = isReady;
	}

	public boolean isMachineActive()
	{
		return powerStatus && networkReady;
	}
}
