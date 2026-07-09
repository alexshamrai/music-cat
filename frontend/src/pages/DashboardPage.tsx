import { useBrowseStats } from '../hooks/useBrowse';

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 p-5">
      <p className="text-sm text-gray-500">{label}</p>
      <p className="text-3xl font-bold text-gray-900 mt-1">{value.toLocaleString()}</p>
    </div>
  );
}

export default function DashboardPage() {
  const { data: stats, isLoading, isError } = useBrowseStats();

  if (isLoading) {
    return (
      <div className="p-4 sm:p-6 lg:p-8">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Dashboard</h2>
        <div className="text-gray-500">Loading stats...</div>
      </div>
    );
  }

  if (isError || !stats) {
    return (
      <div className="p-4 sm:p-6 lg:p-8">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Dashboard</h2>
        <div className="text-red-500">Failed to load stats. Is the backend running?</div>
      </div>
    );
  }

  const grades = [5, 4, 3, 2, 1];
  const maxGradeCount = Math.max(...grades.map(g => stats.gradeDistribution[String(g)] ?? 0), 1);

  return (
    <div className="p-4 sm:p-6 lg:p-8">
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Dashboard</h2>

      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4 mb-8">
        <StatCard label="Artists" value={stats.totalArtists} />
        <StatCard label="Albums" value={stats.totalAlbums} />
        <StatCard label="Songs" value={stats.totalSongs} />
        <StatCard label="Genres" value={stats.totalGenres} />
        <StatCard label="Tags" value={stats.totalTags} />
        <StatCard label="Favorite Artists" value={stats.favoriteArtists} />
        <StatCard label="Favorite Albums" value={stats.favoriteAlbums} />
        <StatCard label="Rated Albums" value={stats.ratedAlbums} />
        <StatCard label="Unrated Albums" value={stats.unratedAlbums} />
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-5">
        <h3 className="text-sm font-medium text-gray-700 mb-4">Grade Distribution</h3>
        <div className="space-y-3">
          {grades.map(grade => {
            const count = stats.gradeDistribution[String(grade)] ?? 0;
            const pct = maxGradeCount > 0 ? (count / maxGradeCount) * 100 : 0;
            return (
              <div key={grade} className="flex items-center gap-3">
                <span className="text-sm text-yellow-500 w-20 shrink-0">{'★'.repeat(grade)}{'☆'.repeat(5 - grade)}</span>
                <div className="flex-1 bg-gray-100 rounded-full h-4 overflow-hidden">
                  <div
                    className="bg-yellow-400 h-4 rounded-full transition-all"
                    style={{ width: `${pct}%` }}
                  />
                </div>
                <span className="text-sm text-gray-600 w-12 text-right shrink-0">{count.toLocaleString()}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
