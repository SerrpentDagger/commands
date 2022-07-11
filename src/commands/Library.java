package commands;

public class Library
{
	public final Runnable load;
	public final String name;
	private boolean loaded = false;
	
	public Library(String name, Runnable onLoad)
	{
		this.name = name;
		this.load = onLoad;
	}
	
	public void load()
	{
		if (!loaded)
			load.run();
		loaded = true;
	}
	
	public boolean isLoaded()
	{
		return loaded;
	}
	
	public String getInfoString()
	{
		return name + " | Loaded: " + loaded;
	}
}
