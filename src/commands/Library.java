package commands;

public class Library
{
	public final Runnable load;
	public final String name;
	private boolean loaded = false;
	private final Library[] dependancies;
	
	public Library(String name, Runnable onLoad, Library... dependancies)
	{
		this.name = name;
		this.load = onLoad;
		this.dependancies = dependancies;
	}
	
	public void load()
	{
		if (!loaded)
		{
			for (Library lib : dependancies)
				lib.load();
			load.run();
			loaded = true;
		}
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
