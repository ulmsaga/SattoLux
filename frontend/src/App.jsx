import { useEffect, useState } from 'react'
import axios from 'axios'

function App() {
  const [health, setHealth] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    axios.get('/api/health')
      .then(res => setHealth(res.data))
      .catch(err => setError(err.message))
  }, [])

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="bg-white shadow rounded-lg p-8 max-w-md w-full">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">SattoLux</h1>
        <p className="text-gray-600 mb-6">주말 로또 번호 생성기</p>

        <div className="border-t pt-4">
          <h2 className="text-sm font-semibold text-gray-700 mb-2">Backend Health</h2>
          {health && (
            <pre className="bg-green-50 text-green-800 text-xs p-3 rounded overflow-x-auto">
              {JSON.stringify(health, null, 2)}
            </pre>
          )}
          {error && (
            <pre className="bg-red-50 text-red-800 text-xs p-3 rounded">
              Error: {error}
            </pre>
          )}
          {!health && !error && (
            <p className="text-gray-400 text-sm">Connecting...</p>
          )}
        </div>
      </div>
    </div>
  )
}

export default App
