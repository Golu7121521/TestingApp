import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';

import '../services/download_service.dart';
import 'exo_player_screen.dart';

class DownloadsScreen extends StatefulWidget {
  const DownloadsScreen({super.key});

  @override
  State<DownloadsScreen> createState() => _DownloadsScreenState();
}

class _DownloadsScreenState extends State<DownloadsScreen> {
  final _manager = DownloadManager.instance;
  StorageSpace? _space;
  Timer? _spaceTimer;

  @override
  void initState() {
    super.initState();
    _refreshSpace();
    _spaceTimer = Timer.periodic(const Duration(seconds: 5), (_) => _refreshSpace());
  }

  @override
  void dispose() {
    _spaceTimer?.cancel();
    super.dispose();
  }

  Future<void> _refreshSpace() async {
    try {
      final s = await _manager.getStorageSpace();
      if (mounted) setState(() => _space = s);
    } catch (_) {}
  }

  Future<void> _confirmCancel(DownloadTask t, {required bool completed}) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(completed ? 'Delete download?' : 'Cancel download?'),
        content: Text(completed
            ? '"${t.title}" will be removed from this device.'
            : 'Progress for "${t.title}" will be discarded.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Keep')),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(completed ? 'Delete' : 'Cancel download'),
          ),
        ],
      ),
    );
    if (ok == true) {
      await _manager.remove(t.id);
      _refreshSpace();
    }
  }

  void _play(DownloadTask t) {
    final path = t.filePath;
    if (path == null || !File(path).existsSync()) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('File not found on device.')),
      );
      return;
    }
    Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => ExoPlayerScreen(filePath: path, title: t.title),
    ));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Downloads')),
      body: ListenableBuilder(
        listenable: _manager,
        builder: (context, _) {
          final active = _manager.active;
          final done = _manager.completed;
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              _StorageCard(space: _space),
              const SizedBox(height: 20),
              _SectionHeader('Downloading', active.length),
              if (active.isEmpty)
                const _EmptyHint('No active downloads')
              else
                ...active.map((t) => _ActiveTile(
                      task: t,
                      onPause: () => _manager.pause(t.id),
                      onResume: () => _manager.resume(t.id),
                      onCancel: () => _confirmCancel(t, completed: false),
                    )),
              const SizedBox(height: 20),
              _SectionHeader('Downloaded', done.length),
              if (done.isEmpty)
                const _EmptyHint('Completed downloads appear here')
              else
                ...done.map((t) => _DoneTile(
                      task: t,
                      onPlay: () => _play(t),
                      onDelete: () => _confirmCancel(t, completed: true),
                    )),
            ],
          );
        },
      ),
    );
  }
}

class _StorageCard extends StatelessWidget {
  const _StorageCard({required this.space});
  final StorageSpace? space;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Card(
      elevation: 0,
      color: cs.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Icon(Icons.sd_storage_outlined, color: cs.primary),
              const SizedBox(width: 8),
              Text('Device storage', style: Theme.of(context).textTheme.titleSmall),
            ]),
            const SizedBox(height: 12),
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: LinearProgressIndicator(
                minHeight: 8,
                value: space?.fraction,
              ),
            ),
            const SizedBox(height: 8),
            Text(space?.label ?? 'Calculating…',
                style: Theme.of(context).textTheme.bodySmall),
          ],
        ),
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.title, this.count);
  final String title;
  final int count;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Text('$title ($count)', style: Theme.of(context).textTheme.titleMedium),
      );
}

class _EmptyHint extends StatelessWidget {
  const _EmptyHint(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Text(text,
            style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
      );
}

class _ActiveTile extends StatelessWidget {
  const _ActiveTile({
    required this.task,
    required this.onPause,
    required this.onResume,
    required this.onCancel,
  });

  final DownloadTask task;
  final VoidCallback onPause;
  final VoidCallback onResume;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    final paused = task.status == DownloadStatus.paused;
    final failed = task.status == DownloadStatus.error;
    final pct = (task.progress * 100).clamp(0, 100).toStringAsFixed(1);

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 8, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Expanded(
                child: Text(task.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.titleSmall),
              ),
              if (failed || paused)
                IconButton(
                    tooltip: 'Resume',
                    icon: const Icon(Icons.play_arrow_rounded),
                    onPressed: onResume)
              else
                IconButton(
                    tooltip: 'Pause',
                    icon: const Icon(Icons.pause_rounded),
                    onPressed: onPause),
              IconButton(
                  tooltip: 'Cancel',
                  icon: const Icon(Icons.close_rounded),
                  onPressed: onCancel),
            ]),
            Text('${task.quality} · ${task.audioLanguage} · ${task.sizeLabel}',
                style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 10),
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: LinearProgressIndicator(
                value: task.progress > 0 ? task.progress : (paused || failed ? 0 : null),
              ),
            ),
            const SizedBox(height: 6),
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(failed ? (task.error ?? 'Error') : '$pct%',
                      style: Theme.of(context).textTheme.bodySmall),
                  Text(
                    paused || failed
                        ? task.stateLabel
                        : '${task.speedLabel} · ${task.seeds} seeds',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DoneTile extends StatelessWidget {
  const _DoneTile({required this.task, required this.onPlay, required this.onDelete});
  final DownloadTask task;
  final VoidCallback onPlay;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) => Card(
        margin: const EdgeInsets.only(bottom: 12),
        child: ListTile(
          onTap: onPlay,
          leading: CircleAvatar(
            backgroundColor: Theme.of(context).colorScheme.primaryContainer,
            child: const Icon(Icons.play_arrow_rounded),
          ),
          title: Text(task.title, maxLines: 1, overflow: TextOverflow.ellipsis),
          subtitle: Text('${task.quality} · ${task.audioLanguage} · ${task.sizeLabel}'),
          trailing: IconButton(
            tooltip: 'Delete',
            icon: const Icon(Icons.delete_outline),
            onPressed: onDelete,
          ),
        ),
      );
}
