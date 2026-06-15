import defaultSettings from '@/settings'

export function useDynamicTitle(): void {
  document.title = defaultSettings.title
}
