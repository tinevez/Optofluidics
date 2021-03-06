package com.optofluidics.trackmate.visualization;

import static com.optofluidics.Main.OPTOFLUIDICS_LIB_VERSION;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

import org.scijava.plugin.Plugin;

import com.optofluidics.trackmate.visualization.ProfileView.ProfileViewOrientation;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.TrackMatePlugIn_;
import fiji.plugin.trackmate.visualization.ViewFactory;

@Plugin( type = ViewFactory.class )
public class ProfileViewHorizontalFactory extends ProfileViewFactory
{

	private static final String INFO_TEXT = "<html>"
			+ "This is a specialized view for line images: Image sequences "
			+ "with all frames made of just 1 line. The frame raw data is displayed "
			+ "as an intensity profile, and a <u>horizontal</u> kymograph is displayed next. Spots and "
			+ "track are shown on the profile window, and tracks on the kymograph."
			+ "<p>"
			+ "The view does not allow interacting with the data. "
			+ "<p>"
			+ "Launching this view with a source image data that is ont made"
			+ "of single line frames will generate an error."
			+ "<p>"
			+ "Library version: " + OPTOFLUIDICS_LIB_VERSION + ""
			+ "</html>";

	private static final String NAME = "Profile viewer - horizontal";

	private static final String KEY = ProfileView.KEY + "_HORIZONTAL";

	@Override
	public String getInfoText()
	{
		return INFO_TEXT;
	}
	@Override
	public String getKey()
	{
		return KEY;
	}

	@Override
	public String getName()
	{
		return NAME;
	}

	@Override
	public ProfileView create( final Model model, final Settings settings, final SelectionModel selectionModel )
	{
		final ImagePlus imp = settings.imp;
		if ( imp.getHeight() != 1 )
		{
			IJ.error( NAME + " " + OPTOFLUIDICS_LIB_VERSION, "ColumnImgProfiler only works for 1D image sequence. Dimensionality was " + imp.getWidth() + " x " + imp.getHeight() );
			return null;
		}
		else
		{
			return new ProfileView( model, selectionModel, imp, ProfileViewOrientation.HORIZONTAL );
		}

	}

	public static void main( final String[] args )
	{
		ImageJ.main( args );
		new TrackMatePlugIn_().run( "samples/SUM_FakeTracks.tif" );
	}
}
