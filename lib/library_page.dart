import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import 'package:photo_manager/photo_manager.dart';
import 'player_page.dart';
import 'store.dart';

class LibraryPage extends StatefulWidget {
  const LibraryPage({super.key});
  @override
  State<LibraryPage> createState() => _LibraryPageState();
}

enum _Sort { recent, name, duration }

class _LibraryPageState extends State<LibraryPage> {
  List<VideoItem> _all = [];
  List<VideoItem> _continue = [];
  bool _loading = true, _denied = false;
  String _query = '';
  _Sort _sort = _Sort.recent;
  bool _searching = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final r = await VideoScanner.scan();
      _denied = r == null;
      _all = r ?? [];
      final ids = await PlaybackStore.recent();
      _continue = [
        for (final id in ids)
          ..._all.where((v) => v.id == id),
      ];
    } catch (_) {
      _all = [];
    }
    if (mounted) setState(() => _loading = false);
  }

  List<VideoItem> get _shown {
    var l = _all
        .where((v) => v.title.toLowerCase().contains(_query.toLowerCase()))
        .toList();
    switch (_sort) {
      case _Sort.name:
        l.sort((a, b) => a.title.toLowerCase().compareTo(b.title.toLowerCase()));
      case _Sort.duration:
        l.sort((a, b) => b.durationSec.compareTo(a.durationSec));
      case _Sort.recent:
        l.sort((a, b) => b.added.compareTo(a.added));
    }
    return l;
  }

  Future<void> _open(VideoItem v, {bool fromStart = false}) async {
    await Navigator.push(context,
        MaterialPageRoute(builder: (_) => PlayerPage(item: v, fromStart: fromStart)));
    _load();
  }

  Future<void> _pick() async {
    final r = await FilePicker.platform.pickFiles(type: FileType.video);
    final f = r?.files.single;
    if (f?.path == null) return;
    await _open(VideoItem(
      id: 'file:${f!.path}',
      title: f.name,
      durationSec: 0,
      folder: 'Picked',
      added: DateTime.now(),
      path: f.path,
    ));
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: _searching
            ? TextField(
                autofocus: true,
                decoration: const InputDecoration(
                    hintText: 'Search videos', border: InputBorder.none),
                onChanged: (s) => setState(() => _query = s),
              )
            : Row(children: [
                Icon(Icons.play_circle_fill, color: cs.primary),
                const SizedBox(width: 8),
                const Text('Vibe Player',
                    style: TextStyle(fontWeight: FontWeight.w700)),
              ]),
        actions: [
          IconButton(
            tooltip: 'Search',
            icon: Icon(_searching ? Icons.close : Icons.search),
            onPressed: () => setState(() {
              _searching = !_searching;
              _query = '';
            }),
          ),
          PopupMenuButton<_Sort>(
            tooltip: 'Sort',
            icon: const Icon(Icons.sort),
            onSelected: (s) => setState(() => _sort = s),
            itemBuilder: (_) => const [
              PopupMenuItem(value: _Sort.recent, child: Text('Recently added')),
              PopupMenuItem(value: _Sort.name, child: Text('Name')),
              PopupMenuItem(value: _Sort.duration, child: Text('Duration')),
            ],
          ),
          IconButton(
              tooltip: 'Rescan', icon: const Icon(Icons.refresh), onPressed: _load),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        backgroundColor: cs.primary,
        foregroundColor: Colors.white,
        onPressed: _pick,
        icon: const Icon(Icons.video_file),
        label: const Text('Browse'),
      ),
      body: _body(cs),
    );
  }

  Widget _body(ColorScheme cs) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_denied) {
      return _empty(Icons.lock_outline, 'Permission needed',
          'Allow video access to see your local videos, or use Browse to pick a file.',
          action: FilledButton(
              onPressed: () async {
                await PhotoManager.openSetting();
              },
              child: const Text('Open settings')));
    }
    final list = _shown;
    if (list.isEmpty) {
      return _empty(Icons.video_library_outlined, 'No videos found',
          'Nothing on this device yet. Tap Browse to pick a file.',
          action: OutlinedButton(onPressed: _load, child: const Text('Rescan')));
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: CustomScrollView(slivers: [
        if (_continue.isNotEmpty && _query.isEmpty) ...[
          _header('Continue Watching'),
          SliverToBoxAdapter(
            child: SizedBox(
              height: 140,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 16),
                itemCount: _continue.length,
                separatorBuilder: (_, __) => const SizedBox(width: 12),
                itemBuilder: (_, i) =>
                    SizedBox(width: 200, child: _Card(item: _continue[i], onTap: _open)),
              ),
            ),
          ),
        ],
        _header('All Videos (${list.length})'),
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 96),
          sliver: SliverGrid.builder(
            gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                maxCrossAxisExtent: 240,
                mainAxisSpacing: 12,
                crossAxisSpacing: 12,
                childAspectRatio: 1.35),
            itemCount: list.length,
            itemBuilder: (_, i) => _Card(item: list[i], onTap: _open),
          ),
        ),
      ]),
    );
  }

  SliverToBoxAdapter _header(String t) => SliverToBoxAdapter(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Text(t,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
        ),
      );

  Widget _empty(IconData i, String t, String s, {Widget? action}) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(i, size: 64, color: const Color(0xFFA1A1AA)),
            const SizedBox(height: 16),
            Text(t, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            Text(s,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Color(0xFFA1A1AA))),
            if (action != null) ...[const SizedBox(height: 16), action],
          ]),
        ),
      );
}

class _Card extends StatelessWidget {
  final VideoItem item;
  final Future<void> Function(VideoItem, {bool fromStart}) onTap;
  const _Card({required this.item, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: 'Play ${item.title}',
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: () => onTap(item),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(18),
          child: Stack(fit: StackFit.expand, children: [
            FutureBuilder(
              future: thumbFor(item),
              builder: (_, s) => s.data != null
                  ? Image.memory(s.data!, fit: BoxFit.cover, gaplessPlayback: true)
                  : const ColoredBox(color: Color(0xFF1B1B23)),
            ),
            const DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Colors.transparent, Color(0xCC000000)],
                ),
              ),
            ),
            Positioned(
              left: 10, right: 10, bottom: 10,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(item.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                          fontWeight: FontWeight.w600, fontSize: 13)),
                  Text(item.durationSec > 0 ? fmt(item.durationSec) : '',
                      style: const TextStyle(
                          color: Color(0xFFA1A1AA), fontSize: 11)),
                ],
              ),
            ),
            Positioned(
              left: 0, right: 0, bottom: 0,
              child: FutureBuilder<double>(
                future: PlaybackStore.progress(item.id),
                builder: (_, s) => (s.data ?? 0) > 0
                    ? LinearProgressIndicator(
                        value: s.data, minHeight: 3, backgroundColor: Colors.black45)
                    : const SizedBox.shrink(),
              ),
            ),
          ]),
        ),
      ),
    );
  }
}
