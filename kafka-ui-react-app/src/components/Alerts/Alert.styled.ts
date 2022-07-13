import { AlertType } from 'redux/interfaces';
import styled from 'styled-components';

export const Alert = styled.div<{ $type: AlertType }>`
  background-color: ${({ $type, theme }) => theme.alert.color[$type]};
  min-width: 400px;
  min-height: 64px;
  border-radius: 8px;
  padding: 12px;
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  filter: drop-shadow(0px 4px 16px ${({ theme }) => theme.alert.shadow});
  margin-top: 10px;
  line-height: 20px;
`;

export const Title = styled.div`
  font-weight: 500;
  font-size: 14px;
`;

export const Message = styled.div`
  font-weight: normal;
  font-size: 14px;
  margin: 3px 0;

  ol,
  ul {
    padding-left: 25px;
    list-style: auto;
  }
`;
