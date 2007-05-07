package p;

class TestDelegateCreationA {
	int b;
}

public class TestDelegateCreation extends TestDelegateCreationA{
	String a[];
	public static class FooParameter {
		public String[] newA;
		public int newB;
		public double newD;
		public FooParameter(String[] newA, int newB, double newD) {
			this.newA = newA;
			this.newB = newB;
			this.newD = newD;
		}
		public String[] getNewA() {
			return newA;
		}
		public void setNewA(String[] newA) {
			newA = newA;
		}
		public int getNewB() {
			return newB;
		}
		public void setNewB(int newB) {
			newB = newB;
		}
		public double getNewD() {
			return newD;
		}
		public void setNewD(double newD) {
			newD = newD;
		}
	}
	/**
	 * @deprecated Use {@link #foo(FooParameter)} instead
	 */
	public void foo(String[] a, int b, double d){
		foo(new FooParameter(a, b, d));
	}
	public void foo(FooParameter parameterObject){
		double d = parameterObject.getNewD();
		int b = parameterObject.getNewB();
		String[] a = parameterObject.getNewA();
		a=new String[0];
		d=5.7;
		b=6;
	}
}
