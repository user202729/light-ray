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

class Panel extends JPanel {
	BufferedImage background;

	Panel(BufferedImage background){
		this.background=background;
	}
	
	@Override
	protected void paintComponent(Graphics g){
		g.drawImage(background,0,0,null);
	}
}

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

// Represents a lattice point.
final class Pointi implements Comparable<Pointi>{
	final int x,y;
	Pointi(int _x,int _y){x=_x ;y=_y ;}

	Pointi add(Pointi p){ return new Pointi(x+p.x,y+p.y); }
	Pointi sub(Pointi p){ return new Pointi(x-p.x,y-p.y); }
	Pointi mul(int a){ return new Pointi(x*a,y*a); }
	Pointi shr(int a){ return new Pointi(x>>a,y>>a); }

	Pointi neg (){ return new Pointi(-x,-y); }
	/// Rotate 90° clockwise, assuming +x is →, +y is ↓.
	Pointi rt90(){ return new Pointi(-y, x); }
	Pointi lt90(){ return new Pointi( y,-x); }

	int dot  (Pointi p){ return x*p.x + y*p.y; }
	/// Magnitude of the resulting vector when interpret the two
	/// points as vector in 3D space and take the cross product.
	int cross(Pointi p){ return x*p.y - y*p.x; }

	int norm(){ return x*x+y*y; } // dot with itself
	double dist(Pointi p){ return Math.sqrt(sub(p).norm()); }

	/// Return the distance from this point to line AB.
	double distToLine(Pointi a,Pointi b){
		Pointi ab=b.sub(a);
		int norm=ab.norm();
		assert norm>0;
		return Math.abs(sub(a).cross(ab)/Math.sqrt(norm));
	}

	/// Return the distance from this point to line segment AB.
	double distToSegment(Pointi a,Pointi b){
		Pointi ab=b.sub(a);
		if(sub(a).dot(ab)<=0)return dist(a);
		if(sub(b).dot(ab)>=0)return dist(b);
		return distToLine(a,b);
	}

	// Warning: this returns a new instance of (Point) per call.
	Point toPoint(){ return new Point(x,y); }

	@Override
	public int compareTo(Pointi p){
		return x!=p.x?
			Integer.compare(x,p.x):Integer.compare(y,p.y);
	}

	@Override
	public boolean equals(Object o){
		if(o==null||!(o instanceof Pointi))return false;
		Pointi p=(Pointi)o;
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
	double dist(Point p){ return Math.sqrt(sub(p).norm()); }

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
		return this.sub(t.mul(2));
	}
}


enum Color{
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
}



public class Main{
	// default configuration. May be used for debugging.
	static String suffix="bmp",path="/home/user202729/test.bmp";
	static boolean antialias=false;

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

	static Edge[][] polygons; // because arrays take less code
	// Used in polygon simplification.
	static final double tolerance=1;

	static class Edge{
		// CombinedEdge, but minimized.
		// Remove information about intermediate points.
		Pointi vertex;
		Color color;
		Edge(Pointi v,Color c){
			vertex=v;color=c;
		}
	}

	static class CombinedEdge{
		// consisting of >=1 pixel edges.
		List<Pointi> edges; // including first, excluding last coord.
		Color color;
		CombinedEdge(Pointi p){
			edges=new ArrayList<>();
			edges.add(p);
		}
		void merge(CombinedEdge e){
			assert color==e.color;
			edges.addAll(e.edges);
		}
	}

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

			final Pointi A=node  .value.edges.get(0),
				B=node.next.next.value.edges.get(0);
			if(A.equals(B)){ // probably can also use ==
				throw new RuntimeException(
					"Invalid polygon. Contains vertex "+A);
			}

			// for performance reason, node.next.value.edges.get(0)
			// should be tested first.
			for(Pointi x:node.next.value.edges)
				if(x.distToSegment(A,B)>tolerance){
					node=node.next;
					continue findEdgeToMerge;
				}
			for(Pointi x:node.value.edges)
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

	static boolean validPolygon(CircularList<CombinedEdge>q,int len){
		if(len!=q.computeLength())return false;
		CircularList<CombinedEdge>.Node node=q.item;
		for(int i=0;i<len;++i){
			final Pointi
				A=node.     value.edges.get(0),
				B=node.next.value.edges.get(0);
			for(Pointi x:node.value.edges)
				if(x.distToSegment(A,B)>tolerance)return false;
			node=node.next;
		}
		return node==q.item;
	}


	static void computePolygons(){
		Pointi[] minPoint=new Pointi[nGroup];
		for(int x=0;x<X;++x)for(int y=0;y<Y;++y){
			int i=groupOf[x][y];
			if(i>=0&&minPoint[i]==null)
				minPoint[i]=new Pointi(x,y);
		}

		assert polygons==null;
		polygons=new Edge[nGroup][];

		for(int group=0;group<nGroup;++group){
			CircularList<CombinedEdge> q;

			// actual processing
			Pointi position=minPoint[group],
				direction=new Pointi(1,0);
			q=new CircularList<>(new CombinedEdge(position));
			CircularList<CombinedEdge>.Node node=q.item;

			while(true){ // traverse the boundary
				// save the pixel color
				Pointi pixel=position.add(
					direction.add(direction.rt90()).shr(1)
				);
				assert groupOf[pixel.x][pixel.y]==group;
				node.value.color=data[pixel.x][pixel.y];

				// advance the position
				position=position.add(direction);

				// check if we finished the cycle
				if(position.equals(node.next.value.edges.get(0)))break;

				// add new position to (q)
				node.addAfter(new CombinedEdge(position));
				node=node.next;

				// rotate the direction
				direction=direction.lt90();
				while(true){
					Pointi dirRT90=direction.rt90();
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
					node.value.edges.get(0),
					node.value.color);

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
				int dist=Color.colorDist(color,colors[index].value);
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
			image.setRGB(x,y,Color.makeBrighter(col.value));

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
			Edge[] polygon=polygons[group];

			int len=polygon.length;
			int[] xs=new int[len],ys=new int[len];
			for(int i=0;i<len;++i){
				xs[i]=polygon[i].vertex.x;
				ys[i]=polygon[i].vertex.y;
			}
			g.drawPolygon(xs,ys,len);
		}
		g.dispose();

		// Create the window.
		// Bottleneck 2: Loading JFrame takes ~0.6s
		JFrame frame=new JFrame();
		Panel panel=new Panel(image);
		frame.add(panel);
		frame.setSize(400,400);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		// Bottleneck 3: This takes ~0.2s
		frame.setVisible(true);

	}
}
