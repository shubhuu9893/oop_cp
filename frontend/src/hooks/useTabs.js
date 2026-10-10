import { useState, useCallback, useEffect } from 'react';
import { webTabs } from '../services/webTabs.js';

export function useTabs() {
  const [tabs, setTabs] = useState([]);
  const [activeTabId, setActiveTabId] = useState(null);

  useEffect(() => {
    const bridge = window.cacheBrowse;
    if (!bridge) {
      setTabs(webTabs.getState().tabs);
      setActiveTabId(webTabs.getState().activeTabId);
      return webTabs.subscribe(state => {
        setTabs(state.tabs);
        setActiveTabId(state.activeTabId);
      });
    }
    bridge.getTabs().then(state => {
      setTabs(state.tabs || []);
      setActiveTabId(state.activeTabId || null);
    }).catch(() => {});
    return bridge.subscribe('tabs', state => {
      setTabs(state.tabs || []);
      setActiveTabId(state.activeTabId || null);
    });
  }, []);

  const addTab = useCallback(async () => {
    if (window.cacheBrowse) await window.cacheBrowse.newTab();
    else webTabs.newTab();
  }, []);

  const closeTab = useCallback(id => window.cacheBrowse ? window.cacheBrowse.closeTab(id) : webTabs.closeTab(id), []);

  const switchTab = useCallback(id => window.cacheBrowse ? window.cacheBrowse.activateTab(id) : webTabs.activateTab(id), []);

  const duplicateTab = useCallback(id => window.cacheBrowse ? window.cacheBrowse.duplicateTab(id) : webTabs.duplicateTab(id), []);

  const activeTab = tabs.find(tab => tab.id === activeTabId) || null;
  return { tabs, activeTab, activeTabId, addTab, closeTab, switchTab, duplicateTab };
}
