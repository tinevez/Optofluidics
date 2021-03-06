package com.optofluidics.trackmate.visualization;

import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Toolbar;
import ij.process.ImageProcessor;
import io.scif.img.ImgIOException;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.File;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.Range;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.XYDataset;

import com.optofluidics.Main;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.ModelChangeEvent;
import fiji.plugin.trackmate.SelectionChangeEvent;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.features.spot.SpotIntensityAnalyzerFactory;
import fiji.plugin.trackmate.features.track.TrackIndexAnalyzer;
import fiji.plugin.trackmate.gui.GuiUtils;
import fiji.plugin.trackmate.io.TmXmlReader;
import fiji.plugin.trackmate.visualization.AbstractTrackMateModelView;
import fiji.plugin.trackmate.visualization.PerTrackFeatureColorGenerator;
import fiji.plugin.trackmate.visualization.SpotColorGenerator;
import fiji.plugin.trackmate.visualization.TrackMateModelView;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayerFactory;
import fiji.plugin.trackmate.visualization.hyperstack.SpotEditTool;
import fiji.plugin.trackmate.visualization.trackscheme.TrackSchemeFactory;

/**
 * A TrackMate model view that specializes for image sequences with individual
 * frames being a single line.
 *
 * @author Jean-Yves Tinevez - 2014
 */
public class ProfileView extends AbstractTrackMateModelView
{

	public static enum ProfileViewOrientation
	{
		VERTICAL, HORIZONTAL;
	}

	private static final String TITLE = "Profile view";

	static final String KEY = "PROFILEVIEW";

	private final double[] Y;

	private final double[] X;

	private final String unit;

	private final double ymin;

	private final double ymax;

	private final String title;

	private final int tmax;

	private final ImagePlus imp;

	private final int width;

	private final ImagePlus kymograph;

	private KymographOverlay kymographOverlay;

	private JFreeChart chart;

	private int frame;

	private ProfileOverlay profileOverlay;

	private final ProfileViewOrientation orientation;

	private JSlider slider;

	public ProfileView( final Model model, final SelectionModel selectionModel, final ImagePlus imp )
	{
		this( model, selectionModel, imp, ProfileViewOrientation.VERTICAL );
	}

	public ProfileView( final Model model, final SelectionModel selectionModel, final ImagePlus imp, final ProfileViewOrientation orientation )
	{
		super( model, selectionModel );
		this.imp = imp;
		this.orientation = orientation;
		if ( imp.getHeight() != 1 ) { throw new IllegalArgumentException( "ColumnImgProfiler only works for 1D image sequence. Dimensionality was " + imp.getWidth() + " x " + imp.getHeight() ); }

		switch ( orientation )
		{
		case HORIZONTAL:
			this.kymograph = KymographGenerator.fromLineImageHorizontal( imp );
			break;

		default:
			this.kymograph = KymographGenerator.fromLineImageVertical( imp );
			break;
		}
		this.unit = imp.getCalibration().getUnits();
		this.title = imp.getTitle();
		this.Y = new double[ imp.getWidth() ];
		this.X = new double[ Y.length ];
		for ( int i = 0; i < X.length; i++ )
		{
			X[ i ] = i * imp.getCalibration().pixelWidth;
		}

		this.tmax = imp.getStackSize();
		this.width = imp.getWidth();
		this.ymin = kymograph.getProcessor().getStatistics().min;
		this.ymax = kymograph.getProcessor().getStatistics().max;
	}

	public void map( final int t )
	{
		if ( t < 0 || t >= tmax ) { return; }
		final ImageProcessor ip = imp.getStack().getProcessor( t + 1 );
		for ( int i = 0; i < width; i++ )
		{
			Y[ i ] = ip.getf( i );
		}
	}

	@Override
	public void render()
	{

		/*
		 * Kymograph
		 */

		kymograph.show();
		kymograph.setOverlay( new Overlay() );
		kymographOverlay = new KymographOverlay( model, kymograph, displaySettings, imp.getCalibration().pixelWidth, orientation );
		kymograph.getOverlay().add( kymographOverlay );

		/*
		 * Dataset
		 */

		final DefaultXYDataset dataset = new DefaultXYDataset();
		dataset.addSeries( "X Profile", new double[][] { X, Y } );

		/*
		 * Chart
		 */

		chart = createChart( dataset );
		final ChartPanel chartPanel = new ChartPanel( chart ) {
			private static final long serialVersionUID = 1L;

			@Override
			public void restoreAutoBounds()
			{
				super.restoreAutoDomainBounds();
				final XYPlot plot = this.getChart().getXYPlot();
				plot.getRangeAxis().setAutoRange( false );
				plot.getRangeAxis().setRange( ymin * 0.95, ymax * 1.05 );
			}
		};
		chartPanel.setPreferredSize( new Dimension( 500, 270 ) );
		profileOverlay = new ProfileOverlay( model, displaySettings );
		chartPanel.addOverlay( profileOverlay );

		/*
		 * Slider
		 */

		slider = new JSlider( 0, tmax - 1 );
		final ChangeListener listener = new ChangeListener()
		{
			@Override
			public void stateChanged( final ChangeEvent event )
			{
				final JSlider slider = ( JSlider ) event.getSource();
				frame = slider.getValue();
				refresh();
			}
		};
		slider.addChangeListener( listener );

		/*
		 * MouseWheel listener
		 */

		final MouseWheelListener mlListener = new MouseWheelListener()
		{
			@Override
			public void mouseWheelMoved( final MouseWheelEvent e )
			{
				final int rotation = e.getWheelRotation();
				frame += rotation;
				if ( frame < 0 )
				{
					frame = 0;
				}
				if ( frame >= tmax )
				{
					frame = tmax - 1;
				}
				slider.setValue( frame );
				// Will trigger refresh.
			}
		};
		kymograph.getWindow().addMouseWheelListener( mlListener );

		/*
		 * Mouse press listener
		 */

		final String toolName = SpotEditTool.getInstance().getToolName();
		final MouseAdapter mouseAdapter = new MouseAdapter()
		{
			@Override
			public void mousePressed( final MouseEvent event )
			{
				if ( Toolbar.getToolName().equals( toolName ) )
				{
					int frame;
					if ( orientation == ProfileViewOrientation.HORIZONTAL )
					{
						frame = kymograph.getCanvas().offScreenX( event.getX() );
					}
					else
					{
						frame = kymograph.getCanvas().offScreenY( event.getY() );
					}
					slider.setValue( frame );
				}
			};

			@Override
			public void mouseDragged( final MouseEvent event )
			{
				if ( Toolbar.getToolName().equals( toolName ) )
				{
					int frame;
					if ( orientation == ProfileViewOrientation.HORIZONTAL )
					{
						frame = kymograph.getCanvas().offScreenX( event.getX() );
					}
					else
					{
						frame = kymograph.getCanvas().offScreenY( event.getY() );
					}
					slider.setValue( frame );
				}
			};
		};

		kymograph.getCanvas().addMouseListener( mouseAdapter );
		kymograph.getCanvas().addMouseMotionListener( mouseAdapter );

		/*
		 * Main panel
		 */

		final JPanel panel = new JPanel();
		final BorderLayout layout = new BorderLayout();
		panel.setLayout( layout );
		panel.add( chartPanel, BorderLayout.CENTER );
		panel.add( slider, BorderLayout.SOUTH );

		/*
		 * Frame
		 */

		final JFrame frame = new JFrame( TITLE );
		frame.setIconImage( Main.OPTOFLUIDICS_ICON.getImage() );
		frame.addMouseWheelListener( mlListener );
		frame.setContentPane( panel );
		frame.pack();
		GuiUtils.positionWindow( frame, kymograph.getWindow() );
		frame.setVisible( true );

		slider.setValue( 0 );
	}

	private JFreeChart createChart( final XYDataset dataset )
	{
		final NumberAxis xAxis = new NumberAxis( unit );
		xAxis.setAutoRangeIncludesZero( false );

		final NumberAxis yAxis = new NumberAxis( "#" );
		final Range range = new Range( ymin * 0.95, ymax * 1.05 );
		yAxis.setAutoRangeIncludesZero( false );
		yAxis.setRange( range );
		yAxis.setDefaultAutoRange( range );

		final XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
		renderer.setSeriesLinesVisible( 0, true );
		renderer.setSeriesShapesVisible( 0, false );
		renderer.setSeriesPaint( 0, Color.BLACK );

		final XYPlot plot = new XYPlot( dataset, xAxis, yAxis, renderer );
		plot.setOrientation( PlotOrientation.VERTICAL );

		// Further customization
		plot.setBackgroundPaint( Color.lightGray );
		plot.setDomainGridlinePaint( Color.white );
		plot.setRangeGridlinePaint( Color.white );

		// Create chart
		final JFreeChart chart = new JFreeChart( title, JFreeChart.DEFAULT_TITLE_FONT, plot, false );

		return chart;
	}

	@Override
	public void refresh()
	{
		map( frame );
		if ( chart != null && kymographOverlay != null )
		{
			kymographOverlay.setFrame( frame );
			kymograph.updateAndDraw();
			profileOverlay.setFrame( frame );
			chart.fireChartChanged();
		}
		new Thread( "ImagePlus frame changer thread" )
		{
			@Override
			public void run()
			{
				imp.setT( frame + 1 );
			};
		}.start();
	}

	@Override
	public void clear()
	{
		// Unimplemented
	}

	@Override
	public void centerViewOn( final Spot spot )
	{
		final int frame = spot.getFeature( Spot.FRAME ).intValue();
		slider.setValue( frame );
	}

	public void displayFrame( final int frame )
	{
		slider.setValue( frame );
	}

	@Override
	public String getKey()
	{
		switch ( orientation )
		{
		case HORIZONTAL:
			return KEY + "_HORIZONTAL";

		default:
			return KEY;
		}
	}

	@Override
	public void modelChanged( final ModelChangeEvent event )
	{
		refresh();
	}

	@Override
	public void selectionChanged( final SelectionChangeEvent event )
	{
		// Highlight selection
		kymographOverlay.setHighlight( selectionModel.getEdgeSelection() );
		profileOverlay.setSpotSelection( selectionModel.getSpotSelection() );
		profileOverlay.setHighlight( selectionModel.getEdgeSelection() );
		// Center on last spot
		super.selectionChanged( event );
		refresh();
	}

	public ProfileOverlay getProfileOverlay()
	{
		return profileOverlay;
	}

	/*
	 * MAIN method
	 */

	public static void main( final String[] args ) throws ImgIOException
	{
		try
		{
			UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
		}
		catch ( final ClassNotFoundException e )
		{
			e.printStackTrace();
		}
		catch ( final InstantiationException e )
		{
			e.printStackTrace();
		}
		catch ( final IllegalAccessException e )
		{
			e.printStackTrace();
		}
		catch ( final UnsupportedLookAndFeelException e )
		{
			e.printStackTrace();
		}

		ImageJ.main( args );

		// final File file = new File( "samples/SUM_FakeTracks.tif" );
		// final ImagePlus imp = new ImagePlus( file.toString() );
		//
		// final Model model = new Model();

		final File file = new File( "samples/Data/101.0-2015-02-13 163957_ColumnSum.xml" );
		final TmXmlReader reader = new TmXmlReader( file );
		if ( !reader.isReadingOk() )
		{
			System.err.println( reader.getErrorMessage() );
			return;
		}

		final Model model = reader.getModel();
		final Settings settings = new Settings();
		reader.readSettings( settings, null, null, null, null, null );
		final SelectionModel selectionModel = new SelectionModel( model );

		final ProfileView profiler = new ProfileView( model, selectionModel, settings.imp, ProfileViewOrientation.HORIZONTAL );
		profiler.setDisplaySettings( TrackMateModelView.KEY_TRACK_COLORING, new PerTrackFeatureColorGenerator( model, TrackIndexAnalyzer.TRACK_ID ) );
		final SpotColorGenerator scg = new SpotColorGenerator( model );
		scg.setFeature( SpotIntensityAnalyzerFactory.MAX_INTENSITY );
		profiler.setDisplaySettings( TrackMateModelView.KEY_SPOT_COLORING, scg );
		profiler.setDisplaySettings( TrackMateModelView.KEY_DISPLAY_SPOT_NAMES, true );
		profiler.setDisplaySettings( TrackMateModelView.KEY_TRACK_DISPLAY_MODE, TrackMateModelView.TRACK_DISPLAY_MODE_LOCAL_BACKWARD );

		profiler.render();

		new TrackSchemeFactory().create( model, settings, selectionModel ).render();
		new HyperStackDisplayerFactory().create( model, settings, selectionModel ).render();
	}
}
