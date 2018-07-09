import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import static java.util.Arrays.asList;
import static java.lang.System.out;


class MyJPanel extends JPanel {
	BufferedImage background;

	MyJPanel(BufferedImage background){
		this.background=background;
	}
	
	@Override
	protected void paintComponent(Graphics g){
		g.drawImage(background,0,0,null);
	}
}



public class Main{
	// default configuration. May be used for debugging.
	static String suffix="bmp",path="/home/user202729/test.bmp";
	static boolean antialias=false;

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
		return new java.awt.Color(rgb)
			.brighter().brighter().brighter().getRGB();
	}

	static enum Color{
		Comment				(0x000000),
		Background			(0xffffff),
		Mirror				(0x808080),
		Pop					(0xff0000),
		NegateOrDivide		(0xff6a00),
		Swap				(0xffd800),
		IncrementOrAdd		(0x00ffff),
		Align				(0xb200ff),
		Push				(0x0026ff),
		DoubleOrMultiply	(0x00ff21),
		Unused				(0x7f0000),
		Unused2				(0xff00dc);

		public final int value;
		Color(int value){this.value=value;}
	}

	static Color[][] data;
	static int X,Y;
	static int[][] groupOf;
	static int nGroup;

	/// DFS to fill (groupOf) array. May need increasing stack size.
	/// Value to fill: (nGroup)
	static void fillGroup(int x,int y){
		if(groupOf[x][y]>=0||data[x][y]==Color.Background)return;
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
			if(groupOf[x][y]<0&&data[x][y]!=Color.Background){
				fillGroup(x,y); // with (nGroup)
				++nGroup;
			}
		}
	}

	static class CircularList<E>{ // linked list
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

	// Note that (Point) is mutable.
	static final class Point implements Comparable<Point>{
		final int x,y;
		Point(int _x,int _y){x=_x ;y=_y ;}

		Point add(Point p){ return new Point(x+p.x,y+p.y); }
		Point sub(Point p){ return new Point(x-p.x,y-p.y); }
		Point mul(int a){ return new Point(x*a,y*a); }
		Point shr(int a){ return new Point(x>>a,y>>a); }

		Point neg (){ return new Point(-x,-y); }
		/// Rotate 90° clockwise, assuming +x is →, +y is ↓.
		Point rt90(){ return new Point(-y, x); }
		Point lt90(){ return new Point( y,-x); }

		int dot  (Point p){ return x*p.x + y*p.y; }
		/// Magnitude of the resulting vector when interpret the two
		/// points as vector in 3D space and take the cross product.
		int cross(Point p){ return x*p.y - y*p.x; }

		int norm(){ return x*x+y*y; } // dot with itself
		double dist(Point p){ return Math.sqrt(sub(p).norm()); }

		/// Return the distance from this point to line AB.
		double distToLine(Point a,Point b){
			Point ab=b.sub(a);
			int norm=ab.norm();
			assert norm>0;
			return Math.abs(sub(a).cross(ab)/Math.sqrt(norm));
		}

		/// Return the distance from this point to line segment AB.
		double distToSegment(Point a,Point b){
			Point ab=b.sub(a);
			if(sub(a).dot(ab)<=0)return dist(a);
			if(sub(b).dot(ab)>=0)return dist(b);
			return distToLine(a,b);
		}

		@Override
		public int compareTo(Point p){
			return x!=p.x?
				Integer.compare(x,p.x):Integer.compare(y,p.y);
		}

		@Override
		public boolean equals(Object o){
			if(o==null||!(o instanceof Point))return false;
			Point p=(Point)o;
			return x==p.x&&y==p.y;
		}
		@Override
		public int hashCode(){ return x*109+y; }
		@Override
		public String toString(){ return "("+x+", "+y+')'; }
	}

	static Point[][] polygons; // because arrays take less code
	// Used in polygon edge detection.
	static final double tolerance=3;

	static void computePolygons(){
		Point[] minPoint=new Point[nGroup];
		for(int x=0;x<X;++x)for(int y=0;y<Y;++y){
			int i=groupOf[x][y];
			if(i>=0&&minPoint[i]==null)
				minPoint[i]=new Point(x,y);
		}

		assert polygons==null;
		polygons=new Point[nGroup][];

		for(int group=0;group<nGroup;++group){
			CircularList<List<Point>> q;

			// actual processing
			int len=1;
			Point position=minPoint[group],
				direction=new Point(1,0);
			q=new CircularList<List<Point>>(
				new ArrayList<Point>(asList(position)));
			CircularList<List<Point>>.Node node=q.item;

			while(true){ // traverse the boundary
				// first, advance the position
				position=position.add(direction);

				// check if we finished the cycle
				if(position.equals(node.next.value.get(0)))break;

				// add new position to (q)
				node.addAfter(
					new ArrayList<Point>(asList(position)));
				node=node.next;
				++len;

				// rotate the direction
				direction=direction.lt90();
				while(true){
					Point dirRT90=direction.rt90();
					// the pixel the rotation will sweep over
					Point pixel=position.add(
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

			// finished the boundary. Now canonicalize it.
			// note: idleCount is reset when edges are merged.
			findEdgeToMerge:
			for(int idleCount=0;idleCount<len;++idleCount){
				// if(true)break;

				final Point A=node  .value.get(0),
					B=node.next.next.value.get(0);
				if(A.equals(B)){ // probably can also use ==
					throw new RuntimeException(
						"Invalid polygon. Contains vertex "+A);
				}

				// for performance reason, node.next.value.get(0)
				// should be tested first.
				for(Point x:node.next.value)
					if(x.distToSegment(A,B)>tolerance){
						node=node.next;
						continue findEdgeToMerge;
					}
				for(Point x:node.value)
					if(x.distToSegment(A,B)>tolerance){
						node=node.next;
						continue findEdgeToMerge;
					}

				// can merge it.
				node.value.addAll(node.next.value);
				q.removeAfter(node);
				--len;
				idleCount=0;
			}

			assert len==q.computeLength();
			node=q.item;for(int i=0;i<len;++i){
				final Point
					A=node.     value.get(0),
					B=node.next.value.get(0);
				for(Point x:node.value)
					assert(x.distToSegment(A,B)<=tolerance);
				node=node.next;
			}
			assert node==q.item;

			// polygon = map(first, q)
			Point[] polygon=new Point[len];
			node=q.item;
			for(int i=0;i<len;++i,node=node.next)
				polygon[i]=node.value.get(0);

			polygons[group]=polygon;
		}

	}

	public static void main(String[] args){

		for(int i=1;i<args.length;++i){
			String arg=args[i];
			int j=arg.indexOf('=');
			if(j<0){
				out.printf("Invalid parameter: %s%n",arg);
				return;
			}
			String key=arg.substring(0,j),value=arg.substring(j+1);
			switch(arg.substring(0,j)){
			case "suffix":
				suffix=value;
				break;
			case "path":
				path=value;
			case "antialias":
				antialias=Boolean.valueOf(value);
			default:
				out.printf("Unrecognized key: %s%n",arg);
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
		final Color[] colors=Color.values();

		data=new Color[X][Y];
		for(int x=0;x<X;++x)for(int y=0;y<Y;++y){
			int minDist=Integer.MAX_VALUE,minIndex=-1;
			int color=image.getRGB(x,y);
			for(int index=0;index<colors.length;++index){
				int dist=colorDist(color,colors[index].value);
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
			final Color col=colors[minIndex];
			if(col==Color.Unused||col==Color.Unused2){
				out.printf("Unused color at (%d, %d)%n",x,y);
				return;
			}
			data[x][y]=col;
			image.setRGB(x,y,makeBrighter(col.value));

			// Remove comments (but doesn't change the background)
			if(col==Color.Comment)data[x][y]=Color.Background;
		}

		groupPixels();
		for(int x=0;x<X;++x)for(int y=0;y<Y;++y)
			if(data[x][y]==Color.Background)assert groupOf[x][y]<0;

		computePolygons();

		// draw the polygons
		Graphics2D g=image.createGraphics();
		g.setColor(java.awt.Color.BLACK);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,antialias?
			RenderingHints.VALUE_ANTIALIAS_ON:
			RenderingHints.VALUE_ANTIALIAS_OFF);
		for(int group=0;group<nGroup;++group){
			Point[] polygon=polygons[group];

			int len=polygon.length;
			int[] xs=new int[len],ys=new int[len];
			for(int i=0;i<len;++i){
				xs[i]=polygon[i].x;
				ys[i]=polygon[i].y;
			}
			g.drawPolygon(xs,ys,len);
		}
		g.dispose();

		// Create the window.
		// Bottleneck 2: Loading JFrame takes ~0.6s
		JFrame frame=new JFrame();
		MyJPanel panel=new MyJPanel(image);
		frame.add(panel);
		frame.setSize(400,400);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		// Bottleneck 3: This takes ~0.2s
		frame.setVisible(true);

	}
}
