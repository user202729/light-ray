import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.FileInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.math.BigInteger;
import java.util.function.UnaryOperator;
import java.util.function.BinaryOperator;
import javax.swing.*;
import java.util.*;

import static javax.swing.KeyStroke.getKeyStroke;
import static java.util.Arrays.asList;
import static java.lang.System.out;

class CircularList<E>{ // linked list
	// Should this extends AbstractSequentialList?
	class Node{
		E value;
		Node next;
		Node(E v){value=v;}

		void addAfter(E v){
			Node next2=next;
			next=new Node(v);
			next.next=next2;
		}
	}
	Node item; // any item. Not guaranteed to be fixed.
	CircularList(E x){item=new Node(x);item.next=item;}

	void removeAfter(Node node){
		// this method must not be provided as a member method
		// because it may invalidate (item).
		if(node.next==item)item=node;
		if(node==node.next)throw new RuntimeException(
			"Attempt to remove on a singleton circular list");
		node.next=node.next.next;
	}

	int computeLength(){
		int len=0;
		Node node=item;do{
			++len;
			node=node.next;
		}while(node!=item);
		return len;
	}
}

// Lattice point.
final class LPoint implements Comparable<LPoint>{
	final int x,y;
	LPoint(int _x,int _y){x=_x ;y=_y ;}

	LPoint add(LPoint p){ return new LPoint(x+p.x,y+p.y); }
	LPoint sub(LPoint p){ return new LPoint(x-p.x,y-p.y); }
	LPoint mul(int a){ return new LPoint(x*a,y*a); }
	LPoint shr(int a){ return new LPoint(x>>a,y>>a); }

	LPoint neg (){ return new LPoint(-x,-y); }
	/// Rotate 90° clockwise, assuming +x is →, +y is ↓.
	LPoint rt90(){ return new LPoint(-y, x); }
	LPoint lt90(){ return new LPoint( y,-x); }

	int dot  (LPoint p){ return x*p.x + y*p.y; }
	/// Magnitude of the resulting vector when interpret the two
	/// points as vector in 3D space and take the cross product.
	int cross(LPoint p){ return x*p.y - y*p.x; }

	int norm(){ return x*x+y*y; } // dot with itself
	double length(){ return Math.sqrt(norm()); }
	double dist(LPoint p){ return sub(p).length(); }

	/// Return the distance from this point to line AB.
	double distToLine(LPoint a,LPoint b){
		LPoint ab=b.sub(a);
		int norm=ab.norm();
		assert norm>0;
		return Math.abs(sub(a).cross(ab)/Math.sqrt(norm));
	}

	/// Return the distance from this point to line segment AB.
	double distToSegment(LPoint a,LPoint b){
		LPoint ab=b.sub(a);
		if(sub(a).dot(ab)<=0)return dist(a);
		if(sub(b).dot(ab)>=0)return dist(b);
		return distToLine(a,b);
	}

	// Warning: this returns a new instance of (Point) per call.
	Point toPoint(){ return new Point(x,y); }

	@Override
	public int compareTo(LPoint p){
		return x!=p.x?
			Integer.compare(x,p.x):Integer.compare(y,p.y);
	}

	@Override
	public boolean equals(Object o){
		if(o==null||!(o instanceof LPoint))return false;
		LPoint p=(LPoint)o;
		return x==p.x&&y==p.y;
	}
	@Override
	public int hashCode(){ return x*109+y; }
	@Override
	public String toString(){ return "("+x+", "+y+')'; }
}

final class Point{
	final double x,y;
	Point(double _x,double _y){x=_x ;y=_y ;}

	Point add(Point p){ return new Point(x+p.x,y+p.y); }
	Point sub(Point p){ return new Point(x-p.x,y-p.y); }
	Point mul(double a){ return new Point(x*a,y*a); }

	double dot  (Point p){ return x*p.x + y*p.y; }
	double cross(Point p){ return x*p.y - y*p.x; }

	double norm(){ return x*x+y*y; } // dot with itself
	double length(){ return Math.sqrt(norm()); }
	double dist(Point p){ return sub(p).length(); }

	// it isn't really possible to compare two double

	// @Override
	// public int compareTo(Point p){
	// 	return x!=p.x?
	// 		Double.compare(x,p.x):Double.compare(y,p.y);
	// }

	@Override
	public String toString(){ return "("+x+", "+y+')'; }

	Point reflectOverLine(Point a,Point b){
		Point t=sub(a);b=b.sub(a);
		t=t.sub(b.mul(t.dot(b)/b.norm()));
		t=this.sub(t.mul(2));
		return t;
	}

	/**
	 * Return {x,y} where a.mul(x).add(b.mul(y)) ~= this, or {null}
	 * if vector {a} and {b} are parallel.
	 */
	double[] decompose(Point a,Point b){
		double denom=a.cross(b);
		if(denom==0)return null;
		double ide=1/denom; // Inverse of DEnominator
		return new double[]{this.cross(b)*ide,a.cross(this)*ide};
	}
}

// =========================
// Program-specific classes.

enum ColorAction{
	Comment         	(Color.BLACK),
	Background      	(Color.WHITE),
	Mirror          	(Color.GRAY),
	Pop             	(Color.RED),
	NegateOrDivide  	(Color.ORANGE),
	Swap            	(Color.YELLOW),
	IncrementOrAdd  	(Color.CYAN),
	Align           	(0xb200ff), // purple
	Push            	(Color.BLUE),
	DoubleOrMultiply	(Color.GREEN),
	Modifier        	(0x7f0000), // brown
	Unused2         	(0xff00dc); // light purple

	public final int value;
	ColorAction(int v){value=v;}
	ColorAction(Color c){value=c.getRGB()&0xffffff;}

	/// Input format: RGB. Return sum of squared distances.
	static int colorDist(int a,int b){
		int ans=0;
		for(int i=0;i<3;++i){
			int diff=(a-b)&0xFF;
			ans+=diff*diff;
			a>>=8;b>>=8;
		}
		return ans;
	}

	static int makeBrighter(int rgb){
		// this may be made more efficient by inlining 'brighter'
		// source code, or implement something similar
		return new Color(rgb)
			.brighter().brighter().brighter().getRGB();
	}
}

class Edge{
	// CombinedEdge, but minimized.
	// Remove information about intermediate points.
	LPoint vertex;
	ColorAction color;
	Edge(LPoint v,ColorAction c){
		vertex=v;color=c;
	}
}

class CombinedEdge{
	// consisting of >=1 pixel edges.
	List<LPoint> edges; // including first, excluding last coord.
	ColorAction color;
	CombinedEdge(LPoint p){
		edges=new ArrayList<>();
		edges.add(p);
	}
	void merge(CombinedEdge e){
		assert color==e.color;
		edges.addAll(e.edges);
	}

	/// Returns the main vertex.
	LPoint v(){
		return edges.get(0);
	}
}

class Memory{
	final ArrayList<BigInteger>[] blocksMem;
	final ArrayList<BigInteger> mainMem;
	static final Scanner in=new Scanner(System.in);

	@SuppressWarnings("unchecked")
	Memory(int nBlock){
		blocksMem=new ArrayList[nBlock];
		for(int block=0;block<nBlock;++block)
			blocksMem[block]=new ArrayList<>();
		mainMem=new ArrayList<>();
	}

	synchronized void applyFunction(UnaryOperator<BigInteger> fn){
		BigInteger result=fn.apply(getMainLast());
		mainMem.set(mainMem.size()-1,result);
	}

	synchronized void applyFunction(int block,BinaryOperator<BigInteger> fn){
		int lastIndex=mainMem.size()-1;
		ArrayList<BigInteger> blockMem=blocksMem[block];
		mainMem.set(lastIndex,fn.apply(
			getMainLast(),
			blockMem.get(blockMem.size()-1)
		));
	}

	static final BigInteger NEGATIVE_ONE=BigInteger.valueOf(-1);
	/**
	 * Get last element of main memory, read from STDIN (and also
	 * push the value from STDIN to the stack to the main memory)
	 * if there isn't any. In case STDIN is exhaused, return -1.
	 */
	synchronized BigInteger getMainLast(){
		if(mainMem.isEmpty()){
			while(in.hasNextLine()){
				if(in.hasNextBigInteger()){
					BigInteger t=in.nextBigInteger();
					mainMem.add(t);
					return t;
				}
				in.next(".");
			}
			return NEGATIVE_ONE;
		}
		return mainMem.get(mainMem.size()-1);
	}

	// Get last element of block memory or 0.
	synchronized BigInteger getBlockLast(int block){
		ArrayList<BigInteger> blockMem=blocksMem[block];
		if(blockMem.isEmpty())return BigInteger.ZERO;
		return blockMem.get(blockMem.size()-1);
	}
}

class Panel extends JPanel {
	private double zoomFactor;
	private final BufferedImage background;
	private final int X,Y;
	private Point pos,velo; // position and velocity
	private final Memory mem;

	// This will NOT be copied on constructor. So please do
	// not modify it while this Panel instance exists.
	private final Edge[][] polygons;
	private final boolean antialias;

	Panel(BufferedImage b,Edge[][] p,boolean a){
		antialias=a;
		zoomFactor=1;background=b;polygons=p;
		mem=new Memory(p.length);
		pos=new Point(0,0);
		velo=new Point(0.5,0.5);
		X=b.getWidth();
		Y=b.getHeight();
	}

	/// Check if (pos) is inside the bound.
	synchronized boolean inBound(){
		return 0<=pos.x&&pos.x<=X&&
			0<=pos.y&&pos.y<=Y;
	}

	synchronized void printMainMem(){
		for(BigInteger x:mem.mainMem)
			out.println(x);
	}

	synchronized void advance(){
		final Point S=pos,
			T=pos.add(velo);
		/* Now find edges that intersect line segment ST (including
		T, excluding S), and is as close to S as possible */
		double minFactor=2; // anything >1
		int block=-1,index=-1;

		for(int block_=0;block_<polygons.length;++block_){
			Edge[] polygon=polygons[block_];

			Edge edge_a=polygon[0];
			Point a=edge_a.vertex.toPoint();
			ColorAction color=edge_a.color;

			for(int index_=polygon.length;index_-->0;){
				Point b=polygon[index_].vertex.toPoint();

				double[] mn=a.sub(S).decompose(velo,a.sub(b));
				// note that velo == vector(ST)
				if(mn!=null){
					double m=mn[0],n=mn[1];
					if(
						0<m&&  // excluding S
						m<=1&& // including T
						0<=n&&n<=1 // including A and B
					){
						if(m<minFactor){
							minFactor=m;
							block=block_;index=index_;
						}
					}
				}

				// prepare value of (a) for next iteration
				a=b;color=polygon[index_].color;
			}
		}

		pos=T;
		if(block<0){
			repaint();
			return;
		}

		final Edge[] polygon=polygons[block];
		final Point A=polygon[index].vertex.toPoint(),
		B=polygon[(index+1)%polygon.length].vertex.toPoint();

		// reflect over the intersecting edge
		pos=pos.reflectOverLine(A,B);
		velo=pos.sub(S.reflectOverLine(A,B));
		// note that (velo) represents a vector

		final ArrayList<BigInteger>
			mainMem=mem.mainMem,
			blockMem=mem.blocksMem[block];
		final int
			mainLast=mainMem.size()-1,
			blockLast=blockMem.size()-1;
		
		switch(polygon[index].color){
		case Comment:
			assert false: "Comments must have been filtered out";
			break;
		case Background:
			assert false: "Is this even an edge....?";
			break;
		case Mirror:
			break;
		case Pop: // the IP pops from the block
			mainMem.add(mem.getBlockLast(block));
			blockMem.remove(blockLast);
			break;
		case NegateOrDivide:
			mem.applyFunction(BigInteger::negate);
			break;
		case Swap:
			final BigInteger t=mem.getMainLast();
			mainMem.set(mainLast,mem.getBlockLast(block));
			blockMem.set(blockLast,t);
			break;
		case IncrementOrAdd:
			mem.applyFunction(BigInteger.ONE::add);
			break;
		case Align:
			if(true)throw new UnsupportedOperationException();
			break;
		case Push: // the IP pushes to the block
			blockMem.add(mem.getMainLast());
			mainMem.remove(mainLast);
			break;
		case DoubleOrMultiply:
			mem.applyFunction(x->x.shiftLeft(1));
			break;
		case Modifier:
			int index_=index;
			ColorAction pcolor;
			do{ // find the first edge in clockwise order
				++index_;
				if(index_==index){
					throw new RuntimeException("Modifier block?");
				}
				pcolor=polygon[index_].color;
			}while(pcolor!=ColorAction.Modifier);
			switch(pcolor){
				case NegateOrDivide: // floor division
					mem.applyFunction(block,(x,y)->{
						BigInteger[] qr=x.divideAndRemainder(y);
						// q*y+r==x ⇒ x/y==q+r/y
						BigInteger q=qr[0],r=qr[1];
						if(r.signum()*y.signum()<0)
							q=q.subtract(BigInteger.ONE);
						return q;
					});
					break;
				case IncrementOrAdd:
					mem.applyFunction(block,BigInteger::add);
					break;
				case DoubleOrMultiply:
					mem.applyFunction(block,BigInteger::multiply);
					break;
				default:
					throw new RuntimeException(String.format(
						"Color %s cannot be modified",pcolor));
			}
			break;
		case Unused2:
			assert false: "But this is unused... (2)";
			break;
		}

		repaint();
	}

	synchronized void zoomBy(double factor){
		assert factor>0;
		zoomFactor*=factor;
		repaint();
	}
	
	/**
	 * Draw the polygons specified in {polygons}, with zoom factor
	 * {zoomFactor}, to the {Graphics g}.
	 */
	void drawPolygons(Graphics g){
		g.setColor(Color.BLACK);
		for(Edge[] polygon:polygons){
			int len=polygon.length;
			int[] xs=new int[len],ys=new int[len];
			for(int i=0;i<len;++i){
				xs[i]=(int)(polygon[i].vertex.x*zoomFactor);
				ys[i]=(int)(polygon[i].vertex.y*zoomFactor);
			}
			g.drawPolygon(xs,ys,len);
		}
	}

	@Override
	synchronized protected void paintComponent(Graphics g_){
		super.paintComponent(g_);

		Graphics2D g=(Graphics2D)g_;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,antialias?
			RenderingHints.VALUE_ANTIALIAS_ON:
			RenderingHints.VALUE_ANTIALIAS_OFF);
		g.drawImage(background,0,0,
			(int)(X*zoomFactor),
			(int)(Y*zoomFactor),
			null);
		drawPolygons(g);

		final double ballRadius=2;
		g.setColor(Color.RED);
		g.fillOval(
			(int)((pos.x-ballRadius)*zoomFactor),
			(int)((pos.y-ballRadius)*zoomFactor),
			(int)(2*ballRadius*zoomFactor),
			(int)(2*ballRadius*zoomFactor));
	}
}

// My custom frame, with all components included.
class Frame extends JFrame{
	Frame(Panel panel){
		add(panel);

		JMenuBar menuBar=new JMenuBar();
		setJMenuBar(menuBar);

		JMenu runMenu=new JMenu("Run");
		runMenu.setMnemonic(KeyEvent.VK_R);
		menuBar.add(runMenu);

		togglePauseMenu=new JMenuItem(PAUSE_TEXT);
		togglePauseMenu.setMnemonic(KeyEvent.VK_E);
		togglePauseMenu.setAccelerator(getKeyStroke("SPACE"));
		togglePauseMenu.addActionListener(e->togglePause());
		runMenu.add(togglePauseMenu);

		JMenuItem incSpeed=new JMenuItem("Increase speed");
		incSpeed.setMnemonic(KeyEvent.VK_I);
		incSpeed.setAccelerator(getKeyStroke("ctrl CLOSE_BRACKET"));
		incSpeed.addActionListener(e->changeDelayBy(1/1.3));
		runMenu.add(incSpeed);

		JMenuItem decSpeed=new JMenuItem("Decrease speed");
		decSpeed.setMnemonic(KeyEvent.VK_D);
		decSpeed.setAccelerator(getKeyStroke("ctrl OPEN_BRACKET"));
		decSpeed.addActionListener(e->changeDelayBy(1.3));
		runMenu.add(decSpeed);

		JMenu viewMenu=new JMenu("View");
		viewMenu.setMnemonic(KeyEvent.VK_V);
		menuBar.add(viewMenu);

		JMenuItem zoomIn=new JMenuItem("Zoom in");
		zoomIn.setMnemonic(KeyEvent.VK_I);
		zoomIn.setAccelerator(getKeyStroke("ctrl EQUALS"));
		zoomIn.addActionListener(e->panel.zoomBy(1.1));
		viewMenu.add(zoomIn);

		JMenuItem zoomOut=new JMenuItem("Zoom out");
		zoomOut.setMnemonic(KeyEvent.VK_O);
		zoomOut.setAccelerator(getKeyStroke("ctrl MINUS"));
		zoomOut.addActionListener(e->panel.zoomBy(1/1.1));
		viewMenu.add(zoomOut);

		setSize(400,400);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

	static final String PAUSE_TEXT="Pause",CONTINUE_TEXT="Continue";
	JMenuItem togglePauseMenu;
	private boolean paused=false;
	final boolean paused(){return paused;}
	synchronized void togglePause(){
		if(paused){
			paused=false;
			togglePauseMenu.setText(PAUSE_TEXT);
			notify();
		}else{
			paused=true;
			togglePauseMenu.setText(CONTINUE_TEXT);
		}
	}

	/// The delay in millisecond between two consecutive frames.
	private double delay=50;

	// unfortunately double is 64 bit so sync is required.
	// see https://stackoverflow.com/a/11459616
	synchronized long getDelay(){return (long)delay;}
	synchronized void changeDelayBy(double factor){delay*=factor;}
}

public class Main{
	// default configuration. May be used for debugging.
	static String suffix="bmp",path="/home/user202729/test.bmp";
	static boolean antialias=false;

	static ColorAction[][] data;
	static int X,Y;
	static int[][] groupOf;
	static int nGroup;

	/// DFS to fill (groupOf) array. May need increasing stack size.
	/// Value to fill: (nGroup)
	static void fillGroup(int x,int y){
		if(groupOf[x][y]>=0||data[x][y]==ColorAction.Background)return;
		groupOf[x][y]=nGroup;
		if(x>0)  fillGroup(x-1,y  );
		if(y>0)  fillGroup(x  ,y-1);
		if(x<X-1)fillGroup(x+1,y  );
		if(y<Y-1)fillGroup(x  ,y+1);
	}

	/// Return number of groups.
	static void groupPixels(){
		assert groupOf==null;
		assert nGroup==0;
		groupOf=new int[X][Y];
		for(int x=0;x<X;++x)Arrays.fill(groupOf[x],-1);
		for(int x=0;x<X;++x)for(int y=0;y<Y;++y){
			if(groupOf[x][y]<0&&data[x][y]!=ColorAction.Background){
				fillGroup(x,y); // with (nGroup)
				++nGroup;
			}
		}
	}

	static Edge[][] polygons; // because arrays take less code
	// Used in polygon simplification.
	static final double tolerance=2.;

	static int simplifyPolygon(CircularList<CombinedEdge> q){
		int len=q.computeLength();
		CircularList<CombinedEdge>.Node node=q.item;

		// note: idleCount is reset when edges are merged.
		findEdgeToMerge:
		for(int idleCount=0;idleCount<len;++idleCount){
			if(node.value.color!=node.next.value.color){
				node=node.next;
				continue findEdgeToMerge;
			}

			final LPoint A=node  .value.v(),
				B=node.next.next.value.v();
			if(A.equals(B)){ // probably can also use ==
				throw new RuntimeException(
					"Invalid polygon. Contains vertex "+A);
			}

			// for performance reason, node.next.value.v()
			// should be tested first.
			for(LPoint x:node.next.value.edges)
				if(x.distToSegment(A,B)>tolerance){
					node=node.next;
					continue findEdgeToMerge;
				}
			for(LPoint x:node.value.edges)
				if(x.distToSegment(A,B)>tolerance){
					node=node.next;
					continue findEdgeToMerge;
				}

			// can merge it.
			node.value.merge(node.next.value);
			q.removeAfter(node);
			--len;
			idleCount=0;
		}

		assert validPolygon(q,len);
		return len;
	}

	/**
	 * Check for the validity of the polygon simplification
	 * algorithm. If this returns {false}, there is a bug in the
	 * simplifyPolygon function.
	 */
	static boolean validPolygon(CircularList<CombinedEdge>q,int len){
		if(len!=q.computeLength())return false;
		CircularList<CombinedEdge>.Node node=q.item;
		for(int i=0;i<len;++i){
			final LPoint
				A=node.     value.v(),
				B=node.next.value.v();
			for(LPoint x:node.value.edges)
				if(x.distToSegment(A,B)>tolerance)return false;
			node=node.next;
		}
		return node==q.item;
	}


	static void computePolygons(){
		LPoint[] minPoint=new LPoint[nGroup];
		for(int x=0;x<X;++x)for(int y=0;y<Y;++y){
			int i=groupOf[x][y];
			if(i>=0&&minPoint[i]==null)
				minPoint[i]=new LPoint(x,y);
		}

		assert polygons==null;
		polygons=new Edge[nGroup][];

		for(int group=0;group<nGroup;++group){
			CircularList<CombinedEdge> q;

			// actual processing
			LPoint position=minPoint[group],
				direction=new LPoint(1,0);
			q=new CircularList<>(new CombinedEdge(position));
			CircularList<CombinedEdge>.Node node=q.item;

			while(true){ // traverse the boundary
				// save the pixel color
				LPoint pixel=position.add(
					direction.add(direction.rt90()).shr(1)
				);
				assert groupOf[pixel.x][pixel.y]==group;
				node.value.color=data[pixel.x][pixel.y];

				// advance the position
				position=position.add(direction);

				// check if we finished the cycle
				if(position.equals(node.next.value.v()))
					break;

				// add new position to (q)
				node.addAfter(new CombinedEdge(position));
				node=node.next;

				// rotate the direction
				direction=direction.lt90();
				while(true){
					LPoint dirRT90=direction.rt90();
					// the pixel the rotation will sweep over
					pixel=position.add(
						direction.add(dirRT90).shr(1)
					);
					if(
						0<=pixel.x&&pixel.x<X &&
						0<=pixel.y&&pixel.y<Y &&
						groupOf[pixel.x][pixel.y]==group
					)break;
					direction=dirRT90;
				}
			}

			int len=simplifyPolygon(q);

			Edge[] polygon=new Edge[len];
			node=q.item;
			for(int i=0;i<len;++i,node=node.next)
				polygon[i]=new Edge(
					node.value.v(),
					node.value.color);

			polygons[group]=polygon;
		}
	}

	public static void main(String[]args)
	throws InterruptedException{
		for(String arg:args){
			int j=arg.indexOf('=');
			if(j<0){
				out.printf("Invalid parameter: %s%n",arg);
				return;
			}
			String key=arg.substring(0,j),value=arg.substring(j+1);
			switch(key){
			case "suffix":
				suffix=value;
				break;
			case "path":
				path=value;
				break;
			case "antialias":
				antialias=Boolean.valueOf(value);
				break;
			default:
				out.printf("Unrecognized key: %s%n",key);
			}
		}

		// Bottleneck 1: Loading ImageIO takes ~0.4s
		ImageReader reader=
			ImageIO.getImageReadersBySuffix(suffix).next();
		BufferedImage image;

		try{
			try{
				reader.setInput(ImageIO.createImageInputStream(
					new FileInputStream(path)));
			}catch(java.io.FileNotFoundException e){
				out.printf("File %s not found%n",path);
				return;
			}
			image=reader.read(0);
		}catch(java.io.IOException e){
			out.printf("IOException: %n");
			e.printStackTrace();
			return;
		}

		X=image.getWidth();Y=image.getHeight();
		final ColorAction[] colors=ColorAction.values();

		data=new ColorAction[X][Y];
		for(int x=0;x<X;++x)for(int y=0;y<Y;++y){
			int color=image.getRGB(x,y);
			int alpha=color>>>24;
			final ColorAction col;

			if(alpha==0){ // transparent
				col=ColorAction.Background;
			}else if(alpha==0xFF){ // opaque
				int minDist=Integer.MAX_VALUE,minIndex=-1;
				for(int index=0;index<colors.length;++index){
					int dist=ColorAction.colorDist(color,colors[index].value);
					if(dist==minDist)
						minIndex=-1;
					else if(dist<minDist){
						minIndex=index;
						minDist=dist;
					}
				}
				if(minIndex<0){
					out.printf("Ambiguous color at (%d, %d)%n",x,y);
					return;
				}
				col=colors[minIndex];
			}else{
				throw new UnsupportedOperationException(
				"Partially transparent pixel at ("+x+", "+y+")");
			}

			if(col==ColorAction.Unused2){
				out.printf("Unused color at (%d, %d)%n",x,y);
				return;
			}
			data[x][y]=col;
			image.setRGB(x,y,ColorAction.makeBrighter(col.value));

			// Remove comments (but doesn't change the background)
			if(col==ColorAction.Comment)data[x][y]=ColorAction.Background;
		}

		groupPixels();
		for(int x=0;x<X;++x)for(int y=0;y<Y;++y)
			if(data[x][y]==ColorAction.Background)
				assert groupOf[x][y]<0;

		computePolygons();

		// Create the window.
		Panel panel=new Panel(image,polygons,antialias);
		Frame frame=new Frame(panel);
		frame.setVisible(true);

		// Main loop
		while(panel.inBound()){
			Thread.sleep(frame.getDelay());
			synchronized(frame){
				if(frame.paused())
					frame.wait();
			}
			panel.advance();
		}

		panel.printMainMem();

		frame.setVisible(false);
		frame.dispatchEvent(
			new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));
		}
}
