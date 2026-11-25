import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const EchoFxApp());
}

class EchoFxApp extends StatelessWidget {
  const EchoFxApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'ECHO FX',
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF8B5CF6),
          brightness: Brightness.dark,
        ),
      ),
      home: const EchoHome(),
    );
  }
}

class EchoHome extends StatefulWidget {
  const EchoHome({super.key});

  @override
  State<EchoHome> createState() => _EchoHomeState();
}

class _EchoHomeState extends State<EchoHome> {
  static const channel = MethodChannel('echo_fx/audio');

  double level = 55;
  bool running = false;
  bool busy = false;

  String message = 'اضغط تشغيل واسمح باستخدام المايك';

  Future<void> start() async {
    if (busy || running) return;

    setState(() {
      busy = true;
      message = 'جاري تشغيل الصوت...';
    });

    try {
      await channel.invokeMethod('start', {
        'level': level.round(),
      });

      if (!mounted) return;

      setState(() {
        running = true;
        message = 'الصدى يعمل مباشرة';
      });
    } on PlatformException catch (e) {
      if (!mounted) return;

      setState(() {
        running = false;
        message = e.message ?? 'تعذر تشغيل المايك';
      });
    } catch (_) {
      if (!mounted) return;

      setState(() {
        running = false;
        message = 'حدث خطأ أثناء التشغيل';
      });
    } finally {
      if (mounted) {
        setState(() => busy = false);
      }
    }
  }

  Future<void> stop() async {
    if (busy) return;

    setState(() => busy = true);

    try {
      await channel.invokeMethod('stop');
    } catch (_) {}

    if (!mounted) return;

    setState(() {
      running = false;
      busy = false;
      message = 'تم إيقاف التأثير';
    });
  }

  Future<void> changeLevel(double value) async {
    setState(() => level = value);

    if (!running) return;

    try {
      await channel.invokeMethod('setLevel', {
        'level': value.round(),
      });
    } catch (_) {}
  }

  @override
  void dispose() {
    channel.invokeMethod('stop');
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF10101A),
      appBar: AppBar(
        title: const Text(
          'ECHO FX',
          style: TextStyle(
            fontWeight: FontWeight.bold,
            letterSpacing: 2,
          ),
        ),
        centerTitle: true,
        backgroundColor: Colors.transparent,
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(
                  Icons.graphic_eq_rounded,
                  size: 100,
                  color: Color(0xFFAA87FF),
                ),

                const SizedBox(height: 20),

                const Text(
                  'صدى مباشر للصوت',
                  style: TextStyle(
                    fontSize: 25,
                    fontWeight: FontWeight.bold,
                  ),
                ),

                const SizedBox(height: 12),

                Text(
                  message,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    color: Colors.white70,
                    fontSize: 15,
                  ),
                ),

                const SizedBox(height: 45),

                Text(
                  '${level.round()}%',
                  style: const TextStyle(
                    fontSize: 48,
                    fontWeight: FontWeight.bold,
                    color: Color(0xFFAA87FF),
                  ),
                ),

                const Text(
                  'مستوى الصدى',
                  style: TextStyle(color: Colors.white60),
                ),

                const SizedBox(height: 10),

                Slider(
                  value: level,
                  min: 0,
                  max: 100,
                  divisions: 100,
                  onChanged: changeLevel,
                ),

                const Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('بدون صدى'),
                    Text('صدى قوي'),
                  ],
                ),

                const SizedBox(height: 35),

                SizedBox(
                  width: double.infinity,
                  height: 60,
                  child: FilledButton.icon(
                    onPressed: busy ? null : (running ? stop : start),
                    icon: Icon(
                      running
                          ? Icons.stop_rounded
                          : Icons.mic_rounded,
                    ),
                    label: Text(
                      busy
                          ? 'انتظر...'
                          : running
                              ? 'إيقاف'
                              : 'تشغيل المايك',
                      style: const TextStyle(fontSize: 18),
                    ),
                  ),
                ),

                const SizedBox(height: 25),

                const Text(
                  'يفضل استخدام سماعات لتجنب الصفير '
                  'ورجوع الصوت إلى المايك.',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: Colors.orangeAccent,
                    fontSize: 13,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}