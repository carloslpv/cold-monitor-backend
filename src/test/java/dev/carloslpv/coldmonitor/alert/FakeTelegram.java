package dev.carloslpv.coldmonitor.alert;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.awaitility.Awaitility.await;

/** Stands in for the Telegram Bot API and records every sendMessage call. */
record FakeTelegram(HttpServer server, List<Message> messages, Queue<int[]> statuses, Queue<String> bodies) {

	static FakeTelegram start() {
		try {
			var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			var telegram = new FakeTelegram(server, new CopyOnWriteArrayList<>(), new ConcurrentLinkedQueue<>(),
					new ConcurrentLinkedQueue<>());
			server.createContext("/", telegram::handle);
			server.start();
			return telegram;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	String url() {
		return "http://localhost:" + this.server.getAddress().getPort();
	}

	void respondNextWith(int status, String body) {
		this.statuses.add(new int[] { status });
		this.bodies.add(body);
	}

	List<Message> messagesFor(String equipmentCode) {
		return this.messages.stream().filter((message) -> message.text().contains(equipmentCode)).toList();
	}

	List<Message> awaitMessages(String equipmentCode, int count) {
		await().atMost(Duration.ofSeconds(10)).until(() -> messagesFor(equipmentCode).size() >= count);
		return messagesFor(equipmentCode);
	}

	void stop() {
		this.server.stop(0);
	}

	private void handle(HttpExchange exchange) throws IOException {
		var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		this.messages.add(new Message(exchange.getRequestURI().getPath(), body));
		var status = this.statuses.poll();
		var response = (status != null) ? this.bodies.poll() : "{\"ok\":true,\"result\":{}}";
		var bytes = response.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders((status != null) ? status[0] : 200, bytes.length);
		try (var out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	record Message(String path, String body) {

		String text() {
			return JsonPath.read(this.body, "$.text");
		}

	}

}
