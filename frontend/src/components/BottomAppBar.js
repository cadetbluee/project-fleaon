import React, { useState, useCallback } from 'react';
import Sellerform from '../components/Sellerform';
import PortalPopup from '../components/PortalPopup';
import AppBar from '@mui/material/AppBar';
import BottomNavigation from '@mui/material/BottomNavigation';
import BottomNavigationAction from '@mui/material/BottomNavigationAction';
import HomeIcon from '@mui/icons-material/Home';
import SearchIcon from '@mui/icons-material/Search';
import AddCircleIcon from '@mui/icons-material/AddCircle';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import AccountCircleIcon from '@mui/icons-material/AccountCircle';
import '../styles/BottomAppBar.css';

const TabBar = () => {
  const [isSellerformOpen, setSellerformOpen] = useState(false);
  const [value, setValue] = useState(0);

  const openSellerform = useCallback(() => {
    setSellerformOpen(true);
  }, []);

  const closeSellerform = useCallback(() => {
    setSellerformOpen(false);
  }, []);

  const onTabClick = useCallback((event, newValue) => {
    setValue(newValue);
  }, []);

  return (
    <>
      <AppBar position="fixed" sx={{ top: 'auto', bottom: 0, borderTop: '0.5px solid #1C1B1F', boxShadow: "none" }}>
        <BottomNavigation value={value} onChange={onTabClick}>
          <BottomNavigationAction icon={<HomeIcon className="homeIcon" />} className="tab1" />
          <BottomNavigationAction icon={<SearchIcon className="searchIcon" />} className="tab2" />
          <BottomNavigationAction icon={<AddCircleIcon className="AddCircleIcon" />} className="tab3" onClick={openSellerform} />
          <BottomNavigationAction icon={<ChatBubbleOutlineIcon className="chatIcon" />} className="tab4" />
          <BottomNavigationAction icon={<AccountCircleIcon className="accountIcon" />} className="tab5" />
        </BottomNavigation>
      </AppBar>
      {isSellerformOpen && (
        <PortalPopup
          overlayColor="rgba(113, 113, 113, 0.3)"
          placement="Centered"
          onOutsideClick={closeSellerform}
        >
          <Sellerform onClose={closeSellerform} />
        </PortalPopup>
      )}
    </>
  );
};

export default TabBar;