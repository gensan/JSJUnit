package jsjunit.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import com.google.common.io.Closeables;

public final class PhantomJS {
	private static class ProcessDestroyer extends TimerTask {

		private Process p;

		public ProcessDestroyer(Process p) {
			this.p = p;
		}

		@Override
		public void run() {
			p.destroy(); 
		}
	}

	private final Process process;

	private final Thread outThread;
	private final Thread errThread;

	public PhantomJS(OutputStream out, OutputStream err, String... args)
			throws IOException {
		final String phantomJsPath = decidePath();

		List<String> commands = new ArrayList<String>();
		commands.add(phantomJsPath);
		for (String arg : args) {
			commands.add(arg);
		}

		ProcessBuilder processBuilder = new ProcessBuilder(
				commands.toArray(new String[commands.size()]));

		process = processBuilder.start();
		
		TimerTask task = new ProcessDestroyer(process);
		Timer timer = new Timer();
		timer.schedule(task, TimeUnit.SECONDS.toMillis(10));

		outThread = new Thread(new Pipe(new BufferedReader(
				new InputStreamReader(process.getInputStream())),
				new BufferedWriter(new OutputStreamWriter(out))));
		
		errThread = new Thread(new Pipe(new BufferedReader(
				new InputStreamReader(process.getErrorStream())),
				new BufferedWriter(new OutputStreamWriter(err))));

		outThread.start();
		errThread.start();
	}

	private static final String SEPARATOR = File.separator;
	private String decidePath() {
		String directoryPath = System.getProperty("phantomjs.path", "");

		if (!directoryPath.isEmpty() && !directoryPath.endsWith(SEPARATOR)){
			directoryPath = directoryPath + SEPARATOR;
		}
		return directoryPath + "phantomjs";
	}

	public void destroy() {
		while (true) {
			try {
				process.waitFor();
				outThread.join();
				errThread.join();
				break;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static class Pipe implements Runnable {

		public Pipe(BufferedReader reader, BufferedWriter writer) {
			this.reader = reader;
			this.writer = writer;
		}

		private final BufferedReader reader;
		private final BufferedWriter writer;

		@Override
		public void run() {
			String line = null;
			try {
				while ((line = reader.readLine()) != null) {
					writer.write(line);
					writer.write("\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				Closeables.closeQuietly(reader);
				Closeables.closeQuietly(writer);
			}
		}

	}

}
