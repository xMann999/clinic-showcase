package lab.app;

import lab.common.Greeting;

/**
 * Działa z common 1.0 i 2.0. Odkomentowanie wywołania helloV2() pokazuje różnicę między
 * błędem KOMPILACJI (gdy na classpath kompilacji jest 1.0) a NoSuchMethodError w RUNTIME
 * (gdy kompilowano z 2.0, a uruchomiono z 1.0).
 */
public final class App {

	private App() {
	}

	public static void main(String[] args) {
		System.out.println(Greeting.hello());
		// System.out.println(Greeting.helloV2());
	}
}

