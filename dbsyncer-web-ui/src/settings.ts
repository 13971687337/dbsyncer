const settings: Settings = {
  title: import.meta.env.VITE_APP_TITLE,
  sideTheme: 'theme-dark',
  showSettings: false,
  navType: 1,
  tagsView: false,
  fixedHeader: true,
  sidebarLogo: true,
  dynamicTitle: false,
  footerVisible: false,
  footerContent: 'Copyright © 2024-2026 武汉互创联合科技. All Rights Reserved.',
}

export interface Settings {
  title: string
  sideTheme: string
  showSettings: boolean
  navType: number
  tagsView: boolean
  fixedHeader: boolean
  sidebarLogo: boolean
  dynamicTitle: boolean
  footerVisible: boolean
  footerContent: string
}

export default settings
